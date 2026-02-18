package com.mengine.core.persistence;

import com.mengine.model.Trade;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory TradeRepository for testing or when DB is not available.
 */
public class InMemoryTradeRepository implements TradeRepository {

    private final Map<String, List<Trade>> bySymbol = new ConcurrentHashMap<>();
    private static final int MAX_PER_SYMBOL = 10_000;

    @Override
    public void save(Trade trade) {
        bySymbol.computeIfAbsent(trade.getSymbol(), k -> new ArrayList<>()).add(trade);
        List<Trade> list = bySymbol.get(trade.getSymbol());
        while (list.size() > MAX_PER_SYMBOL) {
            list.remove(0);
        }
    }

    @Override
    public void saveBatch(List<Trade> trades) {
        for (Trade t : trades) {
            save(t);
        }
    }

    @Override
    public List<Trade> findRecentBySymbol(String symbol, int limit) {
        List<Trade> list = bySymbol.get(symbol);
        if (list == null) return List.of();
        int size = list.size();
        int from = Math.max(0, size - limit);
        return new ArrayList<>(list.subList(from, size)).reversed();
    }
}
