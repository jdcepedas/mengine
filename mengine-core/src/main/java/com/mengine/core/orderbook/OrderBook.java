package com.mengine.core.orderbook;

import com.mengine.model.Order;
import com.mengine.model.OrderType;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * In-memory order book for a single symbol.
 */
public class OrderBook {

    private final String symbol;
    private final ConcurrentSkipListMap<BigDecimal, PriceLevel> bids;
    private final ConcurrentSkipListMap<BigDecimal, PriceLevel> asks;
    private final ConcurrentHashMap<String, Order> orderIndex;

    public OrderBook(String symbol) {
        this.symbol = symbol;
        this.bids = new ConcurrentSkipListMap<>(Collections.reverseOrder());
        this.asks = new ConcurrentSkipListMap<>();
        this.orderIndex = new ConcurrentHashMap<>();
    }

    public String getSymbol() {
        return symbol;
    }

    public void add(Order order) {
        if (!order.getSymbol().equals(symbol)) {
            throw new IllegalArgumentException("Order symbol " + order.getSymbol() + " does not match book symbol " + symbol);
        }
        orderIndex.put(order.getId(), order);
        if (order.getType() == OrderType.BUY) {
            bids.computeIfAbsent(order.getPrice(), PriceLevel::new).add(order);
        } else {
            asks.computeIfAbsent(order.getPrice(), PriceLevel::new).add(order);
        }
    }

    public void addToLevel(Order order, PriceLevel level) {
        orderIndex.put(order.getId(), order);
        level.addFirst(order);
    }

    public boolean remove(Order order) {
        orderIndex.remove(order.getId());
        PriceLevel level = order.getType() == OrderType.BUY ? bids.get(order.getPrice()) : asks.get(order.getPrice());
        if (level != null) {
            boolean removed = level.remove(order);
            if (removed && level.isEmpty()) {
                if (order.getType() == OrderType.BUY) {
                    bids.remove(order.getPrice());
                } else {
                    asks.remove(order.getPrice());
                }
            }
            return removed;
        }
        return false;
    }

    public void updateOrder(Order oldOrder, Order newOrder) {
        remove(oldOrder);
        add(newOrder);
    }

    public BigDecimal getBestBid() {
        return bids.isEmpty() ? null : bids.firstKey();
    }

    public BigDecimal getBestAsk() {
        return asks.isEmpty() ? null : asks.firstKey();
    }

    public PriceLevel getBestBidLevel() {
        return bids.isEmpty() ? null : bids.firstEntry().getValue();
    }

    public PriceLevel getBestAskLevel() {
        return asks.isEmpty() ? null : asks.firstEntry().getValue();
    }

    public PriceLevel getBidLevel(BigDecimal price) {
        return bids.get(price);
    }

    public PriceLevel getAskLevel(BigDecimal price) {
        return asks.get(price);
    }

    public Order getOrder(String orderId) {
        return orderIndex.get(orderId);
    }

    public List<PriceLevel> getBids() {
        return new ArrayList<>(bids.values());
    }

    public List<PriceLevel> getAsks() {
        return new ArrayList<>(asks.values());
    }

    public Iterable<PriceLevel> getMatchingLevels(OrderType type, BigDecimal price) {
        if (type == OrderType.BUY) {
            return () -> asks.headMap(price, true).values().iterator();
        } else {
            return () -> bids.tailMap(price, true).values().iterator();
        }
    }

    public boolean hasBids() {
        return !bids.isEmpty();
    }

    public boolean hasAsks() {
        return !asks.isEmpty();
    }
}
