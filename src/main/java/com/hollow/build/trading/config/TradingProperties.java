package com.hollow.build.trading.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.math.BigDecimal;
import java.util.List;

/** 公开现货行情和本地模拟实验配置，不配置交易所账户密钥。 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "com.hollow.trading")
public class TradingProperties {

    private String bybitBaseUrl = "https://api.bybit.com";
    private Duration connectTimeout = Duration.ofSeconds(5);
    private Duration requestTimeout = Duration.ofSeconds(10);

    /** 用于 Bybit 查询；application.yml 默认沿用 AI 代理，也允许单独覆盖。 */
    private boolean proxyEnabled = false;
    private String proxyAddress = "127.0.0.1";
    private int proxyPort = 7890;

    private Paper paper = new Paper();

    /** 模拟规则在创建实验账户时冻结；不会影响真实交易账户。 */
    @Getter
    @Setter
    public static class Paper {
        private List<String> allowedSymbols = List.of("BTCUSDT", "ETHUSDT");
        private BigDecimal feeRate = new BigDecimal("0.001");
        private BigDecimal slippageBps = new BigDecimal("5");
        /** 兼容旧配置/冻结规则字段名；现在仅限制买入次数，卖出不占额度。 */
        private int maxTradesPerDay = 1;
        private Duration maxMarketAge = Duration.ofSeconds(60);
        private Duration runTimeout = Duration.ofMinutes(10);
        /** 总开关；账户还需要通过 automation 接口单独启用。 */
        private boolean schedulerEnabled = false;
        private Duration schedulerPollInterval = Duration.ofSeconds(60);
        private int schedulerBatchSize = 20;
        /** 独立于每日模型调度；全局开启后扫描所有带保护计划的模拟持仓。 */
        private boolean protectionEnabled = false;
        private Duration protectionPollInterval = Duration.ofSeconds(60);
        private int protectionBatchSize = 20;
    }
}
