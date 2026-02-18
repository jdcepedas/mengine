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
        // Re-attach level to book when it was removed (e.g. partial fill: remove then add reduced order back)
        if (order.getType() == OrderType.BUY) {
            bids.put(level.getPrice(), level);
        } else {
            asks.put(level.getPrice(), level);
        }
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

    /**
     * Returns price levels that can match an incoming order, in priority order (best price first).
     * Trade when there is overlap: for BUY, asks with price <= order price; for SELL, bids with price >= order price.
     * Trade price is always the passive (resting) order's price.
     * Returns a snapshot list so that when the matcher removes/updates levels during matching, the next level is still processed.
     */
    public List<PriceLevel> getMatchingLevels(OrderType type, BigDecimal price) {
        if (type == OrderType.BUY) {
            return new ArrayList<>(asks.headMap(price, true).values());
        } else {
            List<PriceLevel> out = new ArrayList<>();
            for (PriceLevel level : bids.values()) {
                if (level.getPrice().compareTo(price) >= 0) {
                    out.add(level);
                }
            }
            return out;
        }
    }

    public boolean hasBids() {
        return !bids.isEmpty();
    }

    public boolean hasAsks() {
        return !asks.isEmpty();
    }
}
