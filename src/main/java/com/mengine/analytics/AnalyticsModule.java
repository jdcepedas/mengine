package com.mengine.analytics;

import com.mengine.model.Order;
import com.mengine.model.OrderStatus;
import com.mengine.model.Trade;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Analytics module for trade volume, price statistics, and fill rates.
 * In-memory storage with configurable retention.
 */
public class AnalyticsModule {

    private final ConcurrentHashMap<String, SymbolStats> statsBySymbol = new ConcurrentHashMap<>();
    private final AtomicLong totalOrdersPlaced = new AtomicLong(0);
    private final AtomicLong totalOrdersFilled = new AtomicLong(0);
    private final AtomicLong totalOrdersPartial = new AtomicLong(0);

    public void recordOrderPlaced(Order order) {
        totalOrdersPlaced.incrementAndGet();
        statsBySymbol.computeIfAbsent(order.getSymbol(), k -> new SymbolStats())
                .orderPlaced();
    }

    public void recordOrderMatched(Order order, BigDecimal filledQty) {
        totalOrdersFilled.incrementAndGet();
        statsBySymbol.computeIfAbsent(order.getSymbol(), k -> new SymbolStats())
                .orderFilled();
    }

    public void recordOrderPartial(Order order, BigDecimal filledQty) {
        totalOrdersPartial.incrementAndGet();
        statsBySymbol.computeIfAbsent(order.getSymbol(), k -> new SymbolStats())
                .orderPartial();
    }

    public void recordTrade(Trade trade) {
        statsBySymbol.computeIfAbsent(trade.getSymbol(), k -> new SymbolStats())
                .tradeExecuted(trade.getPrice(), trade.getQuantity());
    }

    public AnalyticsReport getReport(String symbol) {
        SymbolStats stats = statsBySymbol.get(symbol);
        if (stats == null) {
            return new AnalyticsReport(symbol, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, 0, 0, 0);
        }
        return stats.toReport(symbol);
    }

    public AnalyticsReport getOverallReport() {
        BigDecimal totalVolume = BigDecimal.ZERO;
        for (SymbolStats s : statsBySymbol.values()) {
            totalVolume = totalVolume.add(s.getVolume());
        }
        return new AnalyticsReport("ALL", totalVolume, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO,
                totalOrdersPlaced.get(), totalOrdersFilled.get(), totalOrdersPartial.get());
    }

    public double getFillRate() {
        long placed = totalOrdersPlaced.get();
        if (placed == 0) return 0;
        return (double) (totalOrdersFilled.get() + totalOrdersPartial.get()) / placed;
    }

    private static class SymbolStats {
        private final AtomicReference<BigDecimal> volume = new AtomicReference<>(BigDecimal.ZERO);
        private final AtomicReference<BigDecimal> highPrice = new AtomicReference<>(BigDecimal.ZERO);
        private final AtomicReference<BigDecimal> lowPrice = new AtomicReference<>(null);
        private final AtomicReference<BigDecimal> totalPriceQty = new AtomicReference<>(BigDecimal.ZERO);
        private final AtomicLong tradeCount = new AtomicLong(0);
        private final AtomicLong ordersPlaced = new AtomicLong(0);
        private final AtomicLong ordersFilled = new AtomicLong(0);
        private final AtomicLong ordersPartial = new AtomicLong(0);

        void orderPlaced() {
            ordersPlaced.incrementAndGet();
        }

        void orderFilled() {
            ordersFilled.incrementAndGet();
        }

        void orderPartial() {
            ordersPartial.incrementAndGet();
        }

        void tradeExecuted(BigDecimal price, BigDecimal qty) {
            volume.accumulateAndGet(price.multiply(qty), (a, b) -> a.add(b));
            tradeCount.incrementAndGet();
            totalPriceQty.accumulateAndGet(price.multiply(qty), (a, b) -> a.add(b));
            highPrice.accumulateAndGet(price, (a, b) -> a.compareTo(b) > 0 ? a : b);
            lowPrice.accumulateAndGet(price, (a, b) -> a == null || a.compareTo(b) > 0 ? b : a);
        }

        BigDecimal getVolume() {
            return volume.get();
        }

        AnalyticsReport toReport(String symbol) {
            BigDecimal vol = volume.get();
            BigDecimal high = highPrice.get();
            BigDecimal low = lowPrice.get() != null ? lowPrice.get() : BigDecimal.ZERO;
            long tc = tradeCount.get();
            BigDecimal avg = tc > 0 && vol.compareTo(BigDecimal.ZERO) > 0
                    ? totalPriceQty.get().divide(vol, 4, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            return new AnalyticsReport(symbol, vol, high, low, avg,
                    ordersPlaced.get(), ordersFilled.get(), ordersPartial.get());
        }
    }

    public record AnalyticsReport(
            String symbol,
            BigDecimal volume,
            BigDecimal highPrice,
            BigDecimal lowPrice,
            BigDecimal avgPrice,
            long ordersPlaced,
            long ordersFilled,
            long ordersPartial
    ) {}
}
