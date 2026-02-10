package com.mengine.orderbook;

import com.mengine.model.Order;

import java.math.BigDecimal;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * A price level in the order book containing orders at the same price.
 * Maintains FIFO ordering via ConcurrentLinkedDeque for lock-free operations.
 */
public class PriceLevel {

    private final BigDecimal price;
    private final ConcurrentLinkedDeque<Order> orders;

    public PriceLevel(BigDecimal price) {
        this.price = price;
        this.orders = new ConcurrentLinkedDeque<>();
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void add(Order order) {
        orders.addLast(order);
    }

    /** Add order at front - used when restoring partially filled order to preserve FIFO */
    public void addFirst(Order order) {
        orders.addFirst(order);
    }

    public Order peek() {
        return orders.peek();
    }

    public Order poll() {
        return orders.poll();
    }

    public boolean remove(Order order) {
        return orders.remove(order);
    }

    public int size() {
        return orders.size();
    }

    public boolean isEmpty() {
        return orders.isEmpty();
    }

    public Iterator<Order> iterator() {
        return orders.iterator();
    }

    public BigDecimal getTotalQuantity() {
        return orders.stream()
                .map(Order::getRemainingQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
