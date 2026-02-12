package com.mengine.api;

/**
 * JSON response for order submission.
 */
public record OrderResponse(
        String orderId,
        String status,
        String message
) {
    public static OrderResponse accepted(String orderId) {
        return new OrderResponse(orderId, "ACCEPTED", "Order queued for matching");
    }

    public static OrderResponse rejected(String message) {
        return new OrderResponse(null, "REJECTED", message);
    }

    public static OrderResponse bufferFull(String orderId) {
        return new OrderResponse(orderId, "REJECTED", "Buffer full - try again later");
    }
}
