package com.mengine.notification;

import com.mengine.model.Order;
import com.mengine.model.Trade;

import java.util.List;

/**
 * Event published by the notification service.
 */
public record Event(
        EventType type,
        long timestampNs,
        Order order,
        List<Trade> trades
) {

    public static Event orderPlaced(Order order) {
        return new Event(EventType.ORDER_PLACED, System.nanoTime(), order, List.of());
    }

    public static Event orderMatched(Order order, List<Trade> trades) {
        return new Event(EventType.ORDER_MATCHED, System.nanoTime(), order, trades);
    }

    public static Event orderPartial(Order order, List<Trade> trades) {
        return new Event(EventType.ORDER_PARTIAL, System.nanoTime(), order, trades);
    }

    public static Event tradeExecuted(Trade trade) {
        return new Event(EventType.TRADE_EXECUTED, trade.getTimestampNs(), null, List.of(trade));
    }

    public enum EventType {
        ORDER_PLACED,
        ORDER_MATCHED,
        ORDER_PARTIAL,
        TRADE_EXECUTED
    }
}
