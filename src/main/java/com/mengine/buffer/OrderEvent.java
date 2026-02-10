package com.mengine.buffer;

import com.mengine.model.Order;

/**
 * Value class for Disruptor ring buffer. Holds an order for processing.
 */
public class OrderEvent {

    private Order order;

    public Order getOrder() {
        return order;
    }

    public void setOrder(Order order) {
        this.order = order;
    }

    public void clear() {
        this.order = null;
    }
}
