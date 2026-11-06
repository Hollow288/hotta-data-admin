package com.hollow.build.trading.market;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;

/** EMA 以 SMA 为种子，RSI/ATR 使用 Wilder 平滑；所有指标只使用已收盘蜡烛。 */
@Service
public class TechnicalIndicatorService {
    private static final MathContext MC = MathContext.DECIMAL128;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private final BybitMarketClient market;

    public TechnicalIndicatorService(BybitMarketClient market) { this.market = market; }

    public record Summary(String symbol, List<Frame> timeframes, String methodology) {}
    public record Frame(String interval, int closedCandleCount, Long latestClosedStartEpochMillis,
                        long asOfEpochMillis, boolean ema200WarmupSufficient,
                        BigDecimal close, BigDecimal ema20, BigDecimal ema50, BigDecimal ema200,
                        BigDecimal rsi14, BigDecimal macd, BigDecimal macdSignal, BigDecimal macdHistogram,
                        BigDecimal atr14, BigDecimal bollingerLower, BigDecimal bollingerMiddle,
                        BigDecimal bollingerUpper, BigDecimal bollingerPercentB,
                        BigDecimal volumeRatio20, BigDecimal previous20Low, BigDecimal previous20High,
                        String warning) {}

    public Summary summary(String symbol) {
        List<Frame> frames = new ArrayList<>();
        for (String interval : List.of("60", "240", "D")) {
            frames.add(calculate(market.indicatorKlines(symbol, interval)));
        }
        return new Summary(symbol, List.copyOf(frames),
                "1H/4H/1D；仅已收盘 K 线。EMA 为 SMA 种子；RSI/ATR 为 Wilder14；MACD12/26/9，柱值=MACD-信号线；"
                        + "布林20周期总体标准差×2，PercentB为带内位置百分数，可超出0~100且不是概率；"
                        + "量比=最后已收盘量/此前20根均量；高低点为此前20根范围，仅为参考，不能保证支撑阻力有效。");
    }

    public static Frame calculate(SpotKlines source) {
        long step = switch (source.interval()) { case "60" -> 3_600_000L; case "240" -> 14_400_000L;
            case "D" -> 86_400_000L; default -> throw new IllegalArgumentException("技术摘要仅支持60/240/D"); };
        List<SpotKlines.Candle> candles = source.candles().stream().filter(SpotKlines.Candle::closed).toList();
        long previous = -1;
        for (SpotKlines.Candle candle : candles) {
            if (candle.startTimeEpochMillis() <= 0 || candle.startTimeEpochMillis() > source.asOfEpochMillis() - step
                    || (previous > 0 && candle.startTimeEpochMillis() - previous != step)
                    || !positive(candle.open()) || !positive(candle.close()) || !positive(candle.high()) || !positive(candle.low())
                    || candle.high().compareTo(candle.open().max(candle.close())) < 0
                    || candle.low().compareTo(candle.open().min(candle.close())) > 0
                    || candle.volume() == null || candle.volume().signum() < 0) {
                throw new BybitMarketException("指标 K 线有缺口、未闭合或 OHLCV 数据异常，拒绝计算");
            }
            previous = candle.startTimeEpochMillis();
        }
        List<BigDecimal> closes = candles.stream().map(SpotKlines.Candle::close).toList();
        int size = closes.size();
        BigDecimal close = size == 0 ? null : closes.getLast();
        List<BigDecimal> fast = ema(closes, 12);
        List<BigDecimal> slow = ema(closes, 26);
        List<BigDecimal> diffs = new ArrayList<>();
        for (int i = 25; i < size; i++) diffs.add(fast.get(i).subtract(slow.get(i), MC));
        BigDecimal macd = diffs.isEmpty() ? null : diffs.getLast();
        BigDecimal signal = last(ema(diffs, 9));
        BigDecimal middle = null, lower = null, upper = null, percentB = null;
        if (size >= 20) {
            List<BigDecimal> window = closes.subList(size - 20, size);
            middle = mean(window);
            BigDecimal variance = BigDecimal.ZERO;
            for (BigDecimal value : window) variance = variance.add(value.subtract(middle, MC).pow(2, MC), MC);
            BigDecimal width = variance.divide(BigDecimal.valueOf(20), MC).sqrt(MC).multiply(BigDecimal.valueOf(2), MC);
            lower = middle.subtract(width, MC);
            upper = middle.add(width, MC);
            if (width.signum() > 0) percentB = close.subtract(lower, MC).divide(upper.subtract(lower, MC), MC).multiply(HUNDRED, MC);
        }
        BigDecimal volumeRatio = null, low = null, high = null;
        if (size >= 21) {
            var window = candles.subList(size - 21, size - 1);
            BigDecimal volumeMean = mean(window.stream().map(SpotKlines.Candle::volume).toList());
            if (volumeMean.signum() > 0) volumeRatio = candles.getLast().volume().divide(volumeMean, MC);
            low = window.stream().map(SpotKlines.Candle::low).min(BigDecimal::compareTo).orElseThrow();
            high = window.stream().map(SpotKlines.Candle::high).max(BigDecimal::compareTo).orElseThrow();
        }
        BigDecimal gain = null, loss = null, atr = null;
        if (size >= 15) {
            List<BigDecimal> gains = new ArrayList<>(), losses = new ArrayList<>(), ranges = new ArrayList<>();
            for (int i = 1; i < size; i++) {
                var candle = candles.get(i);
                BigDecimal delta = candle.close().subtract(closes.get(i - 1), MC);
                gains.add(delta.max(BigDecimal.ZERO));
                losses.add(delta.negate().max(BigDecimal.ZERO));
                ranges.add(candle.high().subtract(candle.low(), MC)
                        .max(candle.high().subtract(closes.get(i - 1), MC).abs())
                        .max(candle.low().subtract(closes.get(i - 1), MC).abs()));
            }
            gain = wilder(gains, 14); loss = wilder(losses, 14); atr = wilder(ranges, 14);
        }
        BigDecimal rsi = gain == null ? null : gain.signum() == 0 && loss.signum() == 0 ? BigDecimal.valueOf(50)
                : loss.signum() == 0 ? HUNDRED : HUNDRED.subtract(HUNDRED.divide(BigDecimal.ONE.add(gain.divide(loss, MC), MC), MC), MC);
        return new Frame(source.interval(), size, size == 0 ? null : candles.getLast().startTimeEpochMillis(),
                source.asOfEpochMillis(), size >= 600, close, last(ema(closes, 20)), last(ema(closes, 50)),
                last(ema(closes, 200)), rsi, macd, signal, macd == null || signal == null ? null : macd.subtract(signal, MC),
                atr, lower, middle, upper, percentB, volumeRatio, low, high,
                size < 600 ? "不足600根已收盘线；不足指标周期的数值为null，EMA200预热有限" : null);
    }

    private static List<BigDecimal> ema(List<BigDecimal> values, int period) {
        List<BigDecimal> result = new ArrayList<>();
        BigDecimal average = null;
        BigDecimal alpha = BigDecimal.valueOf(2).divide(BigDecimal.valueOf(period + 1), MC);
        for (int i = 0; i < values.size(); i++) {
            if (i == period - 1) average = mean(values.subList(0, period));
            else if (i >= period) average = values.get(i).subtract(average, MC).multiply(alpha, MC).add(average, MC);
            result.add(average);
        }
        return result;
    }
    private static BigDecimal wilder(List<BigDecimal> values, int period) {
        BigDecimal result = mean(values.subList(0, period));
        for (int i = period; i < values.size(); i++) result = result.multiply(BigDecimal.valueOf(period - 1), MC)
                .add(values.get(i), MC).divide(BigDecimal.valueOf(period), MC);
        return result;
    }
    private static BigDecimal mean(List<BigDecimal> values) {
        return values.stream().reduce(BigDecimal.ZERO, (a, b) -> a.add(b, MC)).divide(BigDecimal.valueOf(values.size()), MC);
    }
    private static BigDecimal last(List<BigDecimal> values) { return values.isEmpty() ? null : values.getLast(); }
    private static boolean positive(BigDecimal value) { return value != null && value.signum() > 0; }
}
