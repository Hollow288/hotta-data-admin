package com.hollow.build.fund;

import com.hollow.build.fund.dto.FundReviewDto;
import com.hollow.build.fund.dto.FundSnapshotSyncResultDto;
import com.hollow.build.fund.dto.FundTrendDto;

import java.util.List;

/**
 * 基金监控服务。
 */
public interface FundService {

    /**
     * 添加监控基金：校验代码有效性、取名称/类型入库，并回补历史净值与画像。
     *
     * @param code 基金代码
     * @return 添加成功的基金；代码无效（接口查不到）返回 {@code null}
     */
    Fund addFund(String code);

    /**
     * 取消监控某基金（软停用，保留历史净值）。
     *
     * @param code 基金代码
     */
    void removeFund(String code);

    /**
     * 查询所有监控中的基金。
     *
     * @return 监控中的基金列表
     */
    List<Fund> listMonitored();

    /**
     * 查询某基金近 {@code days} 天的净值涨跌走势。
     *
     * @param code 基金代码
     * @param days 天数区间
     * @return 涨跌走势；基金未被监控返回 {@code null}
     */
    FundTrendDto getTrend(String code, int days);

    /**
     * 查询某基金最新净值与当日涨跌。
     *
     * @param code 基金代码
     * @return 最新净值信息；基金未被监控返回 {@code null}
     */
    FundTrendDto getLatest(String code);

    /**
     * 查询某基金的点评数据包（阶段涨跌 + 回撤/波动 + 排名/规模/经理 + 近期走势），供 AI 点评。
     *
     * @param code 基金代码
     * @return 点评数据；基金未被监控返回 {@code null}
     */
    FundReviewDto getReview(String code);

    /**
     * 增量拉取所有监控中基金的最新净值与画像（定时任务调用）。
     */
    void syncAllLatest();

    /**
     * 手动刷新某基金扩展快照（阶段排名），不拉净值。
     *
     * @param code 基金代码
     * @return 本次刷新结果；基金不存在返回 {@code null}
     */
    FundSnapshotSyncResultDto syncSnapshot(String code);
}
