package com.mengine.model;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of all orders for lookup by ID.
 * Stores both active and completed orders.
 */
public class OrderRegistry {

    private final Map<String, Order> orders = new ConcurrentHashMap<>();

    public void put(Order order) {
        orders.put(order.getId(), order);
    }

    public Order get(String orderId) {
        return orders.get(orderId);
    }

    public Order remove(String orderId) {
        return orders.remove(orderId);
    }
}
