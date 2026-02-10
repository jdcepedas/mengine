package com.mengine.buffer;

import com.mengine.model.Order;
import com.mengine.model.OrderType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ReactiveBufferTest {

    @Test
    void publishAndConsume() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger consumed = new AtomicInteger(0);

        DisruptorBuffer buffer = new DisruptorBuffer(1024, order -> {
            consumed.incrementAndGet();
            if (consumed.get() >= 1) latch.countDown();
        });
        buffer.start();

        Order order = Order.create("O1", "AAPL", OrderType.BUY, new BigDecimal("100"), new BigDecimal("10"));
        assertTrue(buffer.publish(order));

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertEquals(1, consumed.get());
        buffer.shutdown();
    }

    @Test
    void hasCapacity() throws InterruptedException {
        CountDownLatch blockLatch = new CountDownLatch(1);
        DisruptorBuffer buffer = new DisruptorBuffer(4, order -> {
            try {
                blockLatch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        buffer.start();

        assertTrue(buffer.hasCapacity());
        assertTrue(buffer.publish(Order.create("O1", "AAPL", OrderType.BUY, BigDecimal.ONE, BigDecimal.ONE)));
        assertTrue(buffer.publish(Order.create("O2", "AAPL", OrderType.BUY, BigDecimal.ONE, BigDecimal.ONE)));
        assertTrue(buffer.publish(Order.create("O3", "AAPL", OrderType.BUY, BigDecimal.ONE, BigDecimal.ONE)));
        assertTrue(buffer.publish(Order.create("O4", "AAPL", OrderType.BUY, BigDecimal.ONE, BigDecimal.ONE)));
        assertFalse(buffer.publish(Order.create("O5", "AAPL", OrderType.BUY, BigDecimal.ONE, BigDecimal.ONE)));

        blockLatch.countDown();
        buffer.shutdown();
    }
}
