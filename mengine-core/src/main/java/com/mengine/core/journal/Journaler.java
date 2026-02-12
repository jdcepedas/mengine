package com.mengine.core.journal;

import com.lmax.disruptor.EventHandler;
import com.mengine.core.buffer.OrderEvent;
import com.mengine.model.Order;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Disruptor event handler that journals each order to disk.
 */
public class Journaler implements EventHandler<OrderEvent> {

    private final OrderJournal journal;

    public Journaler(Path journalDir) throws IOException {
        this.journal = new OrderJournal(journalDir);
    }

    @Override
    public void onEvent(OrderEvent event, long sequence, boolean endOfBatch) throws Exception {
        Order order = event.getOrder();
        if (order != null) {
            journal.append(order);
        }
    }

    public OrderJournal getJournal() {
        return journal;
    }
}
