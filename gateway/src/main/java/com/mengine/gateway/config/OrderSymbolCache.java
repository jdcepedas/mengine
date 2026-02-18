package com.mengine.gateway.config;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory cache of orderId -> symbol for routing GET /orders/{id} to the correct ME Core partition.
 */
@Component
public class OrderSymbolCache {

    private final ConcurrentHashMap<String, String> orderIdToSymbol = new ConcurrentHashMap<>();

    public void put(String orderId, String symbol) {
        orderIdToSymbol.put(orderId, symbol);
    }

    public String get(String orderId) {
        return orderIdToSymbol.get(orderId);
    }
}
