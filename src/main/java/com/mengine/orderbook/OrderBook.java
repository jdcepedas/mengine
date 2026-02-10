package com.mengine.orderbook;

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
 * Uses ConcurrentSkipListMap for O(log n) price lookup and lock-free reads.
 * Bids: descending order (highest first)
 * Asks: ascending order (lowest first)
 */
public class OrderBook {

    private final String symbol;

    // Bids: price descending (best bid = highest)
    private final ConcurrentSkipListMap<BigDecimal, PriceLevel> bids;

    // Asks: price ascending (best ask = lowest)
    private final ConcurrentSkipListMap<BigDecimal, PriceLevel> asks;

    // Quick lookup by order ID
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

    /**
     * Add order to the appropriate side of the book.
     */
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

    /**
     * Add order to a specific price level (e.g. for partial fill restoration).
     * Caller must ensure the level is the correct one for the order.
     */
    public void addToLevel(Order order, PriceLevel level) {
        orderIndex.put(order.getId(), order);
        level.addFirst(order);
    }

    /**
     * Remove order from the book.
     */
    public boolean remove(Order order) {
        orderIndex.remove(order.getId());
        PriceLevel level = order.getType() == OrderType.BUY
                ? bids.get(order.getPrice())
                : asks.get(order.getPrice());
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

    /**
     * Update order in place (e.g., after partial fill).
     */
    public void updateOrder(Order oldOrder, Order newOrder) {
        remove(oldOrder);
        add(newOrder);
    }

    /**
     * Get best bid (highest buy price).
     */
    public BigDecimal getBestBid() {
        return bids.isEmpty() ? null : bids.firstKey();
    }

    /**
     * Get best ask (lowest sell price).
     */
    public BigDecimal getBestAsk() {
        return asks.isEmpty() ? null : asks.firstKey();
    }

    /**
     * Get best bid price level.
     */
    public PriceLevel getBestBidLevel() {
        return bids.isEmpty() ? null : bids.firstEntry().getValue();
    }

    /**
     * Get best ask price level.
     */
    public PriceLevel getBestAskLevel() {
        return asks.isEmpty() ? null : asks.firstEntry().getValue();
    }

    /**
     * Get price level for bids at given price.
     */
    public PriceLevel getBidLevel(BigDecimal price) {
        return bids.get(price);
    }

    /**
     * Get price level for asks at given price.
     */
    public PriceLevel getAskLevel(BigDecimal price) {
        return asks.get(price);
    }

    /**
     * Get order by ID.
     */
    public Order getOrder(String orderId) {
        return orderIndex.get(orderId);
    }

    /**
     * Get all bids (descending by price).
     */
    public List<PriceLevel> getBids() {
        return new ArrayList<>(bids.values());
    }

    /**
     * Get all asks (ascending by price).
     */
    public List<PriceLevel> getAsks() {
        return new ArrayList<>(asks.values());
    }

    /**
     * Get all orders at or better than price for matching.
     * For BUY: asks at or below buy price (ascending - headMap)
     * For SELL: bids at or above sell price (descending - tailMap)
     */
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
