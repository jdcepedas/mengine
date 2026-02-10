package com.mengine.buffer;

import com.mengine.model.Order;

/**
 * Interface for a lock-free queue that manages incoming order requests.
 * Supports backpressure when buffer is full.
 */
public interface ReactiveBuffer {

    /**
     * Enqueue an order for processing. Non-blocking.
     *
     * @param order the order to enqueue
     * @return true if enqueued successfully, false if buffer is full (backpressure)
     */
    boolean publish(Order order);

    /**
     * Check if the buffer has capacity for more orders.
     */
    boolean hasCapacity();

    /**
     * Get the number of orders currently in the buffer.
     */
    long size();

    /**
     * Get the buffer capacity.
     */
    int capacity();

    /**
     * Start the buffer (begin consuming).
     */
    void start();

    /**
     * Stop the buffer (halt consuming).
     */
    void shutdown();
}
