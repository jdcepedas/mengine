package com.mengine.core.aeron;

import com.mengine.model.Order;
import com.mengine.serialization.OrderCodec;
import io.aeron.Aeron;
import io.aeron.Subscription;
import io.aeron.logbuffer.FragmentHandler;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.SleepingIdleStrategy;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Subscribes to Aeron channel for orders and passes each order to a callback.
 * Designed to run on a dedicated thread; the callback typically publishes to the Input Disruptor.
 */
public class AeronOrderSubscriber implements AutoCloseable {

    private final String channel;
    private final int streamId;
    private final String aeronDirectoryName;
    private final OrderCallback callback;
    private final IdleStrategy idleStrategy;
    private Aeron aeron;
    private Subscription subscription;
    private final AtomicBoolean running = new AtomicBoolean(true);

    @FunctionalInterface
    public interface OrderCallback {
        void onOrder(Order order);
    }

    public AeronOrderSubscriber(String channel, int streamId, OrderCallback callback) {
        this(channel, streamId, null, callback);
    }

    public AeronOrderSubscriber(String channel, int streamId, String aeronDirectoryName, OrderCallback callback) {
        this.channel = channel;
        this.streamId = streamId;
        this.aeronDirectoryName = aeronDirectoryName;
        this.callback = callback;
        this.idleStrategy = new SleepingIdleStrategy(100);
    }

    public void start() {
        Aeron.Context ctx = new Aeron.Context();
        if (aeronDirectoryName != null && !aeronDirectoryName.isBlank()) {
            ctx.aeronDirectoryName(aeronDirectoryName);
        }
        aeron = Aeron.connect(ctx);
        subscription = aeron.addSubscription(channel, streamId);
    }

    /**
     * Poll in a loop. Call from a dedicated thread.
     */
    public void run() {
        FragmentHandler handler = (buffer, offset, length, header) -> {
            byte[] bytes = new byte[length];
            buffer.getBytes(offset, bytes, 0, length);
            Order order = OrderCodec.fromBytes(bytes);
            callback.onOrder(order);
        };

        while (running.get()) {
            int fragmentsRead = subscription.poll(handler, 10);
            idleStrategy.idle(fragmentsRead);
        }
    }

    public void stop() {
        running.set(false);
    }

    @Override
    public void close() {
        if (subscription != null) {
            subscription.close();
        }
        if (aeron != null) {
            aeron.close();
        }
    }
}
