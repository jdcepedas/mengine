package com.mengine.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory store of recent trades per symbol.
 */
public class TradeStore {

    private final int maxTradesPerSymbol;
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<Trade>> tradesBySymbol = new ConcurrentHashMap<>();

    public TradeStore(int maxTradesPerSymbol) {
        this.maxTradesPerSymbol = maxTradesPerSymbol;
    }

    public void add(Trade trade) {
        tradesBySymbol
                .computeIfAbsent(trade.getSymbol(), k -> new CopyOnWriteArrayList<>())
                .add(trade);
        CopyOnWriteArrayList<Trade> list = tradesBySymbol.get(trade.getSymbol());
        while (list.size() > maxTradesPerSymbol) {
            list.remove(0);
        }
    }

    public List<Trade> getRecent(String symbol, int limit) {
        List<Trade> list = tradesBySymbol.get(symbol);
        if (list == null) return List.of();
        int size = list.size();
        int from = Math.max(0, size - limit);
        List<Trade> result = new ArrayList<>(list.subList(from, size));
        Collections.reverse(result);
        return result;
    }
}
