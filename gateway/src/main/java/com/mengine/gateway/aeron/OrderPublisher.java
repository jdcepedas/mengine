package com.mengine.gateway.aeron;

import com.mengine.model.Order;
import com.mengine.serialization.OrderCodec;
import io.aeron.Aeron;
import io.aeron.Publication;
import org.agrona.concurrent.UnsafeBuffer;

import java.nio.ByteBuffer;

/**
 * Publishes orders to an Aeron channel.
 */
public class OrderPublisher implements AutoCloseable {

    private final Aeron aeron;
    private final Publication publication;
    private final UnsafeBuffer buffer = new UnsafeBuffer(ByteBuffer.allocate(4096));

    public OrderPublisher(Aeron aeron, String channel, int streamId) {
        this.aeron = aeron;
        this.publication = aeron.addPublication(channel, streamId);
    }

    public boolean publish(Order order) {
        byte[] bytes = OrderCodec.toBytes(order);
        if (bytes.length > buffer.capacity()) {
            return false;
        }
        buffer.wrap(bytes);
        long result = publication.offer(buffer, 0, bytes.length);
        return result > 0;
    }

    @Override
    public void close() {
        if (publication != null) {
            publication.close();
        }
    }
}
