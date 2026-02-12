package com.mengine.gateway.client;

import com.mengine.model.Trade;

import java.util.List;

/**
 * Query trades (e.g. from database).
 */
public interface TradeQuery {

    List<Trade> findRecentBySymbol(String symbol, int limit);
}
