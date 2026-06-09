package com.hollow.build.fund.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 基金监控模块配置属性，绑定 application.yml 中 {@code com.hollow.fund} 前缀下的配置项。
 *
 * <p>配置示例：
 * <pre>
 * com:
 *   hollow:
 *     fund:
 *       api-token: my_secret              # 写接口（加/删监控）的密钥，机器人请求头带 X-FUND-TOKEN
 *       backfill-days: 365                # 添加基金时回补多少天历史净值
 *       nav-sync-cron: "0 30 23 * * ?"    # 每晚拉取最新净值
 *       nav-backup-cron: "0 30 5 * * ?"   # 次日凌晨兜底再拉一次（覆盖 QDII 延迟）
 *       request-timeout-seconds: 15       # 调用东方财富接口的超时时间（秒）
 *       eastmoney-referer: "http://fund.eastmoney.com/"  # lsjz 接口必须带的 Referer
 *       risk-free-annual-rate: 0          # 夏普比率使用的无风险年化收益率(%)
 * </pre>
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "com.hollow.fund")
public class FundConfigurationProperties {

    /**
     * 写接口（添加/取消监控）校验的密钥。
     * 机器人调用时以 {@code X-FUND-TOKEN} 请求头形式带上，与此值比对。
     * 未配置（空）时表示不校验，写接口完全开放（仅建议内网/自用）。
     */
    private String apiToken;

    /**
     * 添加基金时回补的历史净值天数，默认 365 天（约一年）。
     */
    private int backfillDays = 365;

    /**
     * 每晚拉取最新净值的 cron 表达式，默认每晚 23:30。
     */
    private String navSyncCron = "0 30 23 * * ?";

    /**
     * 次日凌晨兜底再拉一次的 cron 表达式，默认 05:30。
     * QDII 净值更新滞后（T+1），凌晨再拉一次提高当天净值的及时性。
     */
    private String navBackupCron = "0 30 5 * * ?";

    /**
     * 调用东方财富接口的请求超时时间，单位秒，默认 15 秒。
     */
    private int requestTimeoutSeconds = 15;

    /**
     * 调用历史净值接口 {@code lsjz} 时必须携带的 Referer，否则会被拒绝。
     */
    private String eastmoneyReferer = "http://fund.eastmoney.com/";

    /**
     * 计算夏普比率时使用的无风险年化收益率（%），默认 0。
     */
    private BigDecimal riskFreeAnnualRate = BigDecimal.ZERO;
}
