package com.hollow.build.fund;

import com.hollow.build.fund.client.EastMoneyFundClient;
import com.hollow.build.fund.config.FundConfigurationProperties;
import com.hollow.build.fund.dto.FundNavPointDto;
import com.hollow.build.fund.dto.FundPeriodRankDto;
import com.hollow.build.fund.dto.FundReviewDto;
import com.hollow.build.fund.dto.FundSnapshotSyncResultDto;
import com.hollow.build.fund.dto.FundTrendDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * 基金监控服务实现。
 * <p>
 * 数据准确性策略：只存官方真实净值；唯一键(code,date) + upsert 防重并支持修正；
 * 添加时回补历史、定时增量并自动补缺口；对涨跌幅做相邻净值自检。
 * 画像、阶段排名随净值一并每天刷新；点评接口只读取本地快照。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FundServiceImpl implements FundService {

    /** 回补/增量分页拉取时每页条数。 */
    private static final int BACKFILL_PAGE_SIZE = 200;
    private static final int SYNC_PAGE_SIZE = 50;
    /** 翻页安全上限，防止异常情况下死循环。 */
    private static final int MAX_PAGES = 100;
    /** 每次翻页之间的轻微限速（毫秒），降低被限流概率。 */
    private static final long PAGE_INTERVAL_MILLIS = 200L;
    /** 涨跌幅自检的告警阈值（百分点）。 */
    private static final BigDecimal GROWTH_CHECK_THRESHOLD = new BigDecimal("0.1");
    /** 点评/风险指标取近 1 年净值。 */
    private static final int REVIEW_WINDOW_DAYS = 365;
    /** 点评里"近期走势"返回的交易日条数。 */
    private static final int RECENT_POINTS = 10;
    /** 年化波动率的年交易日数。 */
    private static final double TRADING_DAYS_PER_YEAR = 252.0;
    private static final String MONEY_FUND_NOTE = "货币型基金无单位净值涨跌，收益请以万份收益/七日年化为准";

    private final FundMapper fundMapper;
    private final EastMoneyFundClient eastMoneyFundClient;
    private final FundConfigurationProperties fundConfig;

    @Override
    public Fund addFund(String code) {
        EastMoneyFundClient.FundBasic basic = eastMoneyFundClient.searchFund(code);
        if (basic == null || basic.name() == null || basic.name().isBlank()) {
            log.warn("添加监控失败：未找到基金代码 {}", code);
            return null;
        }

        Fund fund = new Fund();
        fund.setFundCode(basic.code());
        fund.setFundName(basic.name());
        fund.setFundType(basic.type());
        fundMapper.insertOrUpdateFund(fund);
        log.info("添加监控基金：{} {} ({})", basic.code(), basic.name(), basic.type());

        // 回补历史净值与画像（同步，便于添加后立刻能查走势/点评）
        backfillNav(code, fundConfig.getBackfillDays());
        refreshProfile(code);
        refreshPeriodRanks(code);
        return fundMapper.selectByCode(code);
    }

    @Override
    public void removeFund(String code) {
        fundMapper.disableFund(code);
        log.info("取消监控基金：{}", code);
    }

    @Override
    public List<Fund> listMonitored() {
        return fundMapper.selectEnabledList();
    }

    @Override
    public FundTrendDto getTrend(String code, int days) {
        Fund fund = fundMapper.selectByCode(code);
        if (fund == null) {
            return null;
        }
        LocalDate startDate = LocalDate.now().minusDays(days);
        List<FundNav> navs = fundMapper.selectNavByCodeAndRange(code, startDate); // 升序

        FundTrendDto dto = baseTrendDto(fund);
        dto.setDays(days);
        dto.setPoints(navs.stream()
                .map(n -> new FundNavPointDto(n.getNavDate(), n.getUnitNav(), n.getGrowthRate()))
                .toList());

        if (!navs.isEmpty()) {
            FundNav latest = navs.get(navs.size() - 1);
            FundNav earliest = navs.get(0);
            dto.setLatestNavDate(latest.getNavDate());
            dto.setLatestNav(latest.getUnitNav());
            dto.setLatestGrowthRate(latest.getGrowthRate());
            dto.setPeriodGrowthRate(percentChange(earliest.getUnitNav(), latest.getUnitNav()));
        }
        return dto;
    }

    @Override
    public FundTrendDto getLatest(String code) {
        Fund fund = fundMapper.selectByCode(code);
        if (fund == null) {
            return null;
        }
        FundNav latest = fundMapper.selectLatestNav(code);
        FundTrendDto dto = baseTrendDto(fund);
        dto.setDays(0);
        if (latest != null) {
            dto.setLatestNavDate(latest.getNavDate());
            dto.setLatestNav(latest.getUnitNav());
            dto.setLatestGrowthRate(latest.getGrowthRate());
            dto.setPoints(List.of(new FundNavPointDto(latest.getNavDate(), latest.getUnitNav(), latest.getGrowthRate())));
        } else {
            dto.setPoints(List.of());
        }
        return dto;
    }

    @Override
    public FundReviewDto getReview(String code) {
        Fund fund = fundMapper.selectByCode(code);
        if (fund == null) {
            return null;
        }
        FundReviewDto dto = new FundReviewDto();
        dto.setFundCode(fund.getFundCode());
        dto.setFundName(fund.getFundName());
        dto.setFundType(fund.getFundType());

        // 近 1 年净值：算最新值、最高/最低、最大回撤、波动率、近期走势
        List<FundNav> navs = fundMapper.selectNavByCodeAndRange(code, LocalDate.now().minusDays(REVIEW_WINDOW_DAYS));
        if (!navs.isEmpty()) {
            FundNav latest = navs.get(navs.size() - 1);
            dto.setAsOf(latest.getNavDate());
            dto.setLatestNav(latest.getUnitNav());
            dto.setLatestGrowthRate(latest.getGrowthRate());

            FundNav high = navs.get(0);
            FundNav low = navs.get(0);
            for (FundNav n : navs) {
                if (n.getUnitNav() == null) {
                    continue;
                }
                if (high.getUnitNav() == null || n.getUnitNav().compareTo(high.getUnitNav()) > 0) {
                    high = n;
                }
                if (low.getUnitNav() == null || n.getUnitNav().compareTo(low.getUnitNav()) < 0) {
                    low = n;
                }
            }
            dto.setHighNav(high.getUnitNav());
            dto.setHighDate(high.getNavDate());
            dto.setLowNav(low.getUnitNav());
            dto.setLowDate(low.getNavDate());
            BigDecimal maxDrawdown = computeMaxDrawdown(navs);
            BigDecimal volatility = computeVolatility(navs);
            BigDecimal annualizedReturn = computeAnnualizedReturn(navs);
            dto.setMaxDrawdown(maxDrawdown);
            dto.setVolatility(volatility);
            dto.setAnnualizedReturn(annualizedReturn);
            dto.setSharpeRatio(computeSharpeRatio(annualizedReturn, volatility));
            dto.setCalmarRatio(computeCalmarRatio(annualizedReturn, maxDrawdown));

            int from = Math.max(0, navs.size() - RECENT_POINTS);
            dto.setRecent(navs.subList(from, navs.size()).stream()
                    .map(n -> new FundNavPointDto(n.getNavDate(), n.getUnitNav(), n.getGrowthRate()))
                    .toList());
        } else {
            dto.setRecent(List.of());
        }

        // 画像快照：阶段涨跌、同类排名、规模、经理、费率
        FundProfile p = fundMapper.selectProfileByCode(code);
        if (p != null) {
            dto.setReturn1m(p.getReturn1m());
            dto.setReturn3m(p.getReturn3m());
            dto.setReturn6m(p.getReturn6m());
            dto.setReturn1y(p.getReturn1y());
            dto.setSimilarPercent(p.getSimilarPercent());
            dto.setFundScale(p.getFundScale());
            dto.setManagerName(p.getManagerName());
            dto.setManagerStar(p.getManagerStar());
            dto.setManagerWorkTime(p.getManagerWorkTime());
            dto.setManagerSize(p.getManagerSize());
            dto.setManagerScore(p.getManagerScore());
            dto.setBuyRate(p.getBuyRate());
        }

        List<FundPeriodRank> ranks = fundMapper.selectPeriodRanksByCode(code);
        dto.setPeriodRanks(ranks.stream()
                .map(r -> new FundPeriodRankDto(r.getPeriodCode(), r.getPeriodName(), r.getFundReturn(),
                        r.getSimilarAverageReturn(), r.getBenchmarkReturn(), r.getRankNo(), r.getRankTotal(), r.getAsOf()))
                .toList());

        StringBuilder note = new StringBuilder();
        if (fund.getFundName() != null && fund.getFundName().contains("货币")) {
            note.append(MONEY_FUND_NOTE).append(' ');
        }
        if (p == null) {
            note.append("画像数据(排名/规模/经理)暂缺，定时任务会自动补全。");
        }
        if (ranks.isEmpty()) {
            note.append("阶段排名数据暂缺，定时任务会自动补全。");
        }
        if (!note.isEmpty()) {
            dto.setNote(note.toString().trim());
        }
        return dto;
    }

    @Override
    public void syncAllLatest() {
        List<Fund> funds = fundMapper.selectEnabledList();
        log.info("开始增量拉取基金净值与画像，共 {} 只", funds.size());
        for (Fund fund : funds) {
            syncLatest(fund.getFundCode()); // 单只内部已做异常隔离
        }
        log.info("基金净值与画像拉取完成");
    }

    @Override
    public FundSnapshotSyncResultDto syncSnapshot(String code) {
        Fund fund = fundMapper.selectByCode(code);
        if (fund == null) {
            return null;
        }
        FundSnapshotSyncResultDto result = new FundSnapshotSyncResultDto();
        result.setFundCode(fund.getFundCode());
        result.setPeriodRankCount(refreshPeriodRanks(code));
        return result;
    }

    /**
     * 回补某基金近 {@code days} 天的历史净值。分页倒序拉取，逐页 upsert，拉到目标起始日为止。
     */
    private void backfillNav(String code, int days) {
        LocalDate startDate = LocalDate.now().minusDays(days);
        try {
            int pageIndex = 1;
            while (pageIndex <= MAX_PAGES) {
                EastMoneyFundClient.NavPage page = eastMoneyFundClient.fetchNavHistory(code, pageIndex, BACKFILL_PAGE_SIZE);
                List<FundNav> list = page.list();
                if (list.isEmpty()) {
                    break;
                }
                List<FundNav> toSave = list.stream()
                        .filter(n -> !n.getNavDate().isBefore(startDate))
                        .toList();
                if (!toSave.isEmpty()) {
                    fundMapper.batchUpsertNav(toSave);
                }
                // 接口按日期倒序，本页最后一条是最旧的；已早于起始日则无需再翻页
                LocalDate oldestInPage = list.get(list.size() - 1).getNavDate();
                if (oldestInPage.isBefore(startDate) || (long) pageIndex * BACKFILL_PAGE_SIZE >= page.totalCount()) {
                    break;
                }
                pageIndex++;
                sleepQuietly();
            }
            refreshLatestNavDate(code);
            log.info("回补历史净值完成 code={} 起始日={}", code, startDate);
        } catch (Exception e) {
            log.error("回补历史净值失败 code={}", code, e);
        }
    }

    /**
     * 增量拉取某基金最新净值，并在与库内最新日期之间存在缺口时自动往前补齐，最后刷新画像。
     */
    private void syncLatest(String code) {
        try {
            LocalDate maxDate = fundMapper.selectMaxNavDate(code);
            int pageIndex = 1;
            while (pageIndex <= MAX_PAGES) {
                EastMoneyFundClient.NavPage page = eastMoneyFundClient.fetchNavHistory(code, pageIndex, SYNC_PAGE_SIZE);
                List<FundNav> list = page.list();
                if (list.isEmpty()) {
                    break;
                }
                List<FundNav> toSave = (maxDate == null)
                        ? list
                        : list.stream().filter(n -> n.getNavDate().isAfter(maxDate)).toList();
                if (!toSave.isEmpty()) {
                    fundMapper.batchUpsertNav(toSave);
                }
                // 新基金（库里没有任何净值）只拉一页，剩余历史交给回补逻辑
                if (maxDate == null) {
                    break;
                }
                // 本页已覆盖到库内最新日期，说明没有更早的缺口了
                LocalDate oldestInPage = list.get(list.size() - 1).getNavDate();
                if (!oldestInPage.isAfter(maxDate) || (long) pageIndex * SYNC_PAGE_SIZE >= page.totalCount()) {
                    break;
                }
                pageIndex++;
                sleepQuietly();
            }
            refreshLatestNavDate(code);
            verifyGrowthRate(code);
        } catch (Exception e) {
            log.error("增量拉取净值失败 code={}", code, e);
        }
        refreshProfile(code);
        refreshPeriodRanks(code);
    }

    /** 拉取并覆盖更新基金画像快照。 */
    private void refreshProfile(String code) {
        try {
            FundProfile profile = eastMoneyFundClient.fetchProfile(code);
            if (profile != null) {
                fundMapper.upsertProfile(profile);
            }
        } catch (Exception e) {
            log.error("刷新基金画像失败 code={}", code, e);
        }
    }

    /** 拉取并覆盖更新阶段收益排名快照。 */
    private int refreshPeriodRanks(String code) {
        try {
            List<FundPeriodRank> ranks = eastMoneyFundClient.fetchPeriodRanks(code);
            if (!ranks.isEmpty()) {
                fundMapper.batchUpsertPeriodRank(ranks);
                log.info("刷新基金阶段排名完成 code={} count={}", code, ranks.size());
                return ranks.size();
            }
            log.warn("刷新基金阶段排名无数据 code={}", code);
        } catch (Exception e) {
            log.error("刷新基金阶段排名失败 code={}", code, e);
        }
        return 0;
    }

    /** 用库内最大净值日期刷新 fund.latest_nav_date。 */
    private void refreshLatestNavDate(String code) {
        LocalDate max = fundMapper.selectMaxNavDate(code);
        if (max != null) {
            fundMapper.updateLatestNavDate(code, max);
        }
    }

    /**
     * 涨跌幅自检：用相邻两日净值算出涨跌幅，与接口给的最新涨跌幅比对，差异过大则告警。
     */
    private void verifyGrowthRate(String code) {
        try {
            List<FundNav> recent = fundMapper.selectNavByCodeAndRange(code, LocalDate.now().minusDays(15));
            if (recent.size() < 2) {
                return;
            }
            FundNav today = recent.get(recent.size() - 1);
            FundNav prev = recent.get(recent.size() - 2);
            if (today.getGrowthRate() == null || today.getUnitNav() == null
                    || prev.getUnitNav() == null || prev.getUnitNav().signum() == 0) {
                return;
            }
            BigDecimal calc = today.getUnitNav().subtract(prev.getUnitNav())
                    .divide(prev.getUnitNav(), 6, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
            if (calc.subtract(today.getGrowthRate()).abs().compareTo(GROWTH_CHECK_THRESHOLD) > 0) {
                log.warn("净值涨跌幅自检异常 code={} date={} 接口值={}% 计算值={}%",
                        code, today.getNavDate(), today.getGrowthRate(), calc.setScale(4, RoundingMode.HALF_UP));
            }
        } catch (Exception e) {
            log.debug("涨跌幅自检跳过 code={}: {}", code, e.getMessage());
        }
    }

    /** 计算区间最大回撤(%)：历史峰值到后续低点的最大跌幅。 */
    private BigDecimal computeMaxDrawdown(List<FundNav> navs) {
        BigDecimal peak = null;
        BigDecimal maxDrawdown = BigDecimal.ZERO;
        for (FundNav n : navs) {
            BigDecimal v = n.getUnitNav();
            if (v == null) {
                continue;
            }
            if (peak == null || v.compareTo(peak) > 0) {
                peak = v;
            }
            if (peak.signum() != 0) {
                BigDecimal dd = v.subtract(peak).divide(peak, 6, RoundingMode.HALF_UP);
                if (dd.compareTo(maxDrawdown) < 0) {
                    maxDrawdown = dd;
                }
            }
        }
        return maxDrawdown.multiply(BigDecimal.valueOf(100)).setScale(4, RoundingMode.HALF_UP);
    }

    /** 计算年化波动率(%)：日涨跌幅的标准差 × √252 × 100。 */
    private BigDecimal computeVolatility(List<FundNav> navs) {
        List<Double> returns = new ArrayList<>();
        for (FundNav n : navs) {
            if (n.getGrowthRate() != null) {
                returns.add(n.getGrowthRate().doubleValue() / 100.0);
            }
        }
        if (returns.size() < 2) {
            return null;
        }
        double mean = returns.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double variance = returns.stream().mapToDouble(r -> (r - mean) * (r - mean)).sum() / (returns.size() - 1);
        double annualized = Math.sqrt(variance) * Math.sqrt(TRADING_DAYS_PER_YEAR) * 100.0;
        return BigDecimal.valueOf(annualized).setScale(4, RoundingMode.HALF_UP);
    }

    /** 计算区间年化收益率(%)：按首尾净值和实际天数年化。 */
    private BigDecimal computeAnnualizedReturn(List<FundNav> navs) {
        if (navs.size() < 2) {
            return null;
        }
        FundNav first = navs.get(0);
        FundNav latest = navs.get(navs.size() - 1);
        if (first.getUnitNav() == null || latest.getUnitNav() == null || first.getUnitNav().signum() == 0) {
            return null;
        }
        long days = ChronoUnit.DAYS.between(first.getNavDate(), latest.getNavDate());
        if (days <= 0) {
            return null;
        }
        double ratio = latest.getUnitNav().divide(first.getUnitNav(), 10, RoundingMode.HALF_UP).doubleValue();
        double annualized = (Math.pow(ratio, 365.0 / days) - 1.0) * 100.0;
        return BigDecimal.valueOf(annualized).setScale(4, RoundingMode.HALF_UP);
    }

    /** 计算夏普比率：(年化收益率 - 无风险年化收益率) / 年化波动率。 */
    private BigDecimal computeSharpeRatio(BigDecimal annualizedReturn, BigDecimal volatility) {
        if (annualizedReturn == null || volatility == null || volatility.signum() == 0) {
            return null;
        }
        BigDecimal riskFree = fundConfig.getRiskFreeAnnualRate() == null
                ? BigDecimal.ZERO
                : fundConfig.getRiskFreeAnnualRate();
        return annualizedReturn.subtract(riskFree)
                .divide(volatility, 6, RoundingMode.HALF_UP)
                .setScale(4, RoundingMode.HALF_UP);
    }

    /** 计算卡玛比率：年化收益率 / 最大回撤绝对值。 */
    private BigDecimal computeCalmarRatio(BigDecimal annualizedReturn, BigDecimal maxDrawdown) {
        if (annualizedReturn == null || maxDrawdown == null || maxDrawdown.signum() == 0) {
            return null;
        }
        return annualizedReturn
                .divide(maxDrawdown.abs(), 6, RoundingMode.HALF_UP)
                .setScale(4, RoundingMode.HALF_UP);
    }

    /** 用基金信息构造走势 DTO 骨架，含货币型提示。 */
    private FundTrendDto baseTrendDto(Fund fund) {
        FundTrendDto dto = new FundTrendDto();
        dto.setFundCode(fund.getFundCode());
        dto.setFundName(fund.getFundName());
        dto.setFundType(fund.getFundType());
        if (fund.getFundName() != null && fund.getFundName().contains("货币")) {
            dto.setNote(MONEY_FUND_NOTE);
        }
        return dto;
    }

    /** 计算 (to - from) / from * 100，保留 4 位；非法输入返回 null。 */
    private BigDecimal percentChange(BigDecimal from, BigDecimal to) {
        if (from == null || to == null || from.signum() == 0) {
            return null;
        }
        return to.subtract(from)
                .divide(from, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(4, RoundingMode.HALF_UP);
    }

    private void sleepQuietly() {
        try {
            Thread.sleep(PAGE_INTERVAL_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
