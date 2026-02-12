package com.mengine.core.persistence;

import com.mengine.model.Trade;

import java.util.List;

/**
 * Persistence for trades (e.g. to database).
 */
public interface TradeRepository {

    void save(Trade trade);

    void saveBatch(List<Trade> trades);

    List<Trade> findRecentBySymbol(String symbol, int limit);
}
