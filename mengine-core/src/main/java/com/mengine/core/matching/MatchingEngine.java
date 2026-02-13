package com.mengine.core.matching;

import com.mengine.core.orderbook.OrderBook;
import com.mengine.core.orderbook.PriceLevel;
import com.mengine.model.Order;
import com.mengine.model.OrderType;
import com.mengine.model.Trade;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Core matching engine with price-time priority.
 */
public class MatchingEngine {

    private final long matchingTimeoutNs;
    private final Map<String, OrderBook> orderBooks = new ConcurrentHashMap<>();
    private final AtomicLong tradeIdGenerator = new AtomicLong(0);
    private final AtomicLong orderIdGenerator = new AtomicLong(0);

    public MatchingEngine(long matchingTimeoutMs) {
        this.matchingTimeoutNs = matchingTimeoutMs * 1_000_000;
    }

    public OrderBook getOrCreateOrderBook(String symbol) {
        return orderBooks.computeIfAbsent(symbol, OrderBook::new);
    }

    public OrderBook getOrderBook(String symbol) {
        return orderBooks.get(symbol);
    }

    public MatchResult match(Order order) {
        System.out.println("[ME Core] Income Matching order: " + order.getSymbol() + " " + order.getType() + " " + order.getPrice() + " " + order.getQuantity());
        long startNs = System.nanoTime();
        long deadlineNs = startNs + matchingTimeoutNs;

        OrderBook book = getOrCreateOrderBook(order.getSymbol());
        List<Trade> trades = new ArrayList<>();
        Order currentOrder = order;

        for (PriceLevel level : book.getMatchingLevels(order.getType(), order.getPrice())) {
            if (System.nanoTime() > deadlineNs) {
                break;
            }

            while (!level.isEmpty()) {
                Order restingOrder = level.peek();
                if (restingOrder == null) break;

                BigDecimal fillQty = currentOrder.getRemainingQuantity().min(restingOrder.getRemainingQuantity());
                if (fillQty.compareTo(BigDecimal.ZERO) <= 0) break;

                String buyerId = order.getType() == OrderType.BUY ? currentOrder.getId() : restingOrder.getId();
                String sellerId = order.getType() == OrderType.SELL ? currentOrder.getId() : restingOrder.getId();
                BigDecimal matchPrice = restingOrder.getPrice();

                Trade trade = Trade.builder()
                        .id("T" + tradeIdGenerator.incrementAndGet())
                        .symbol(order.getSymbol())
                        .buyOrderId(buyerId)
                        .sellOrderId(sellerId)
                        .price(matchPrice)
                        .quantity(fillQty)
                        .timestampNs(System.nanoTime())
                        .build();
                trades.add(trade);

                BigDecimal newRestingRemaining = restingOrder.getRemainingQuantity().subtract(fillQty);
                Order updatedResting = restingOrder.withRemainingQuantity(newRestingRemaining);
                if (newRestingRemaining.compareTo(BigDecimal.ZERO) == 0) {
                    level.poll();
                    book.remove(restingOrder);
                } else {
                    level.poll();
                    book.remove(restingOrder);
                    book.addToLevel(updatedResting, level);
                }

                BigDecimal newCurrentRemaining = currentOrder.getRemainingQuantity().subtract(fillQty);
                currentOrder = currentOrder.withRemainingQuantity(newCurrentRemaining);

                if (newCurrentRemaining.compareTo(BigDecimal.ZERO) == 0) {
                    long elapsedNs = System.nanoTime() - startNs;
                    return new MatchResult(currentOrder, trades, true, false, elapsedNs);
                }
            }
        }

        if (currentOrder.getRemainingQuantity().compareTo(order.getQuantity()) < 0) {
            book.add(currentOrder);
            long elapsedNs = System.nanoTime() - startNs;
            return new MatchResult(currentOrder, trades, false, true, elapsedNs);
        }

        if (currentOrder.getRemainingQuantity().compareTo(BigDecimal.ZERO) > 0) {
            book.add(currentOrder);
        }

        long elapsedNs = System.nanoTime() - startNs;
        // NOTE - use return directly instead of creating object
        System.out.println("[ME Core] MatchResult: " + trades.size() + " trades were  matched " + " elapsedNs=" + elapsedNs);
        trades.forEach(t -> System.out.println("[ME Core] Trade: " + t.getId() + " " + t.getSymbol() + " " + t.getPrice() + " " + t.getQuantity()));
        return new MatchResult(currentOrder, trades, false, !trades.isEmpty(), elapsedNs);
    }

    public String generateOrderId() {
        return "O" + orderIdGenerator.incrementAndGet();
    }
}
