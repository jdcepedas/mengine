package com.mengine.gateway.aeron;

import com.mengine.model.Order;
import com.mengine.serialization.OrderCodec;
import io.aeron.Aeron;
import io.aeron.Publication;
import org.agrona.concurrent.UnsafeBuffer;

import java.nio.ByteBuffer;

/**
 * Publishes orders to an Aeron channel.
 * offer() returns negative on failure: NOT_CONNECTED when no subscriber is connected
 * (e.g. ME Core not running or using a different Media Driver dir), BACK_PRESSURED when buffer full.
 *
 * <p>Buffer behavior: a single reusable UnsafeBuffer backs a fixed ByteBuffer. Before each offer we
 * wrap the serialized order byte[] with that buffer (no copy). Serialized size is JSON (OrderCodec),
 * so it varies with price/quantity precision and symbol/id length. We reject orders whose serialized
 * size exceeds {@link #MAX_ORDER_MESSAGE_BYTES} so we never offer oversized messages.
 */
public class OrderPublisher implements AutoCloseable {

    /** Max serialized order size (JSON). Orders with very long price/quantity decimals can approach this. */
    public static final int MAX_ORDER_MESSAGE_BYTES = 32 * 1024;

    private final Aeron aeron;
    private final Publication publication;
    private final UnsafeBuffer buffer = new UnsafeBuffer(ByteBuffer.allocate(MAX_ORDER_MESSAGE_BYTES));
    private final String channel;
    private final int streamId;

    public OrderPublisher(Aeron aeron, String channel, int streamId) {
        this.aeron = aeron;
        this.channel = channel;
        this.streamId = streamId;
        this.publication = aeron.addPublication(channel, streamId);
    }

    public boolean publish(Order order) {
        byte[] bytes = OrderCodec.toBytes(order);
        if (bytes.length > MAX_ORDER_MESSAGE_BYTES) {
            return false;
        }
        buffer.wrap(bytes);
        long result = publication.offer(buffer, 0, bytes.length);
        if (result <= 0) {
            System.out.println("[Gateway] offer FAILED result=" + result
                + " isConnected=" + publication.isConnected()
                + " orderId=" + order.getId() + " type=" + order.getType() + " symbol=" + order.getSymbol()
                + " (-1=NOT_CONNECTED -2=BACK_PRESSURED)");
        }
        return result > 0;
    }

    @Override
    public void close() {
        if (publication != null) {
            publication.close();
        }
    }
}
