package com.mengine.model;

/**
 * Status of an order in the matching engine.
 */
public enum OrderStatus {
    PENDING,    // Order placed, awaiting match
    PARTIAL,    // Partially filled
    MATCHED,    // Fully filled
    CANCELLED   // Order cancelled
}
