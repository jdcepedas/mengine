package com.mengine.core.buffer;

import com.mengine.model.Order;

/**
 * Value class for Input Disruptor ring buffer.
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
