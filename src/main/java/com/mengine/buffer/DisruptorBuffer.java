package com.mengine.buffer;

import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.util.DaemonThreadFactory;
import com.mengine.model.Order;

import java.util.concurrent.atomic.AtomicLong;

/**
 * LMAX Disruptor implementation of ReactiveBuffer.
 * Ultra-low latency lock-free ring buffer for order ingestion.
 * Single producer, single consumer pattern.
 */
public class DisruptorBuffer implements ReactiveBuffer {

    private final RingBuffer<OrderEvent> ringBuffer;
    private final Disruptor<OrderEvent> disruptor;
    private final int capacity;
    private final AtomicLong publishedCount = new AtomicLong(0);
    private final AtomicLong consumedCount = new AtomicLong(0);

    public DisruptorBuffer(int bufferSize, OrderConsumer consumer) {
        this.capacity = bufferSize;
        this.disruptor = new Disruptor<>(
                OrderEvent::new,
                bufferSize,
                DaemonThreadFactory.INSTANCE
        );

        disruptor.handleEventsWith((event, sequence, endOfBatch) -> {
            try {
                Order order = event.getOrder();
                if (order != null) {
                    consumer.consume(order);
                }
            } finally {
                event.clear();
                consumedCount.incrementAndGet();
            }
        });

        this.ringBuffer = disruptor.getRingBuffer();
    }

    @Override
    public boolean publish(Order order) {
        try {
            long sequence = ringBuffer.tryNext();
            try {
                OrderEvent event = ringBuffer.get(sequence);
                event.setOrder(order);
            } finally {
                ringBuffer.publish(sequence);
            }
            publishedCount.incrementAndGet();
            return true;
        } catch (com.lmax.disruptor.InsufficientCapacityException e) {
            return false;
        }
    }

    @Override
    public boolean hasCapacity() {
        return ringBuffer.remainingCapacity() > 0;
    }

    @Override
    public long size() {
        return publishedCount.get() - consumedCount.get();
    }

    @Override
    public int capacity() {
        return capacity;
    }

    @Override
    public void start() {
        disruptor.start();
    }

    @Override
    public void shutdown() {
        disruptor.shutdown();
    }

    /**
     * Consumer interface for processing orders from the buffer.
     */
    @FunctionalInterface
    public interface OrderConsumer {
        void consume(Order order);
    }
}
