package com.mengine.core.buffer;

import com.mengine.model.Trade;

/**
 * Value class for Output Disruptor ring buffer.
 */
public class TradeEvent {

    private Trade trade;

    public Trade getTrade() {
        return trade;
    }

    public void setTrade(Trade trade) {
        this.trade = trade;
    }

    public void clear() {
        this.trade = null;
    }
}
