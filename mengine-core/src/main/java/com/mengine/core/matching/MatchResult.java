package com.mengine.core.matching;

import com.mengine.model.Order;
import com.mengine.model.Trade;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Result of matching an order against the order book.
 */
public class MatchResult {

    private final Order order;
    private final List<Trade> trades;
    private final boolean matched;
    private final boolean partial;
    private final long matchingTimeNs;

    public MatchResult(Order order, List<Trade> trades, boolean matched, boolean partial, long matchingTimeNs) {
        this.order = order;
        this.trades = trades != null ? new ArrayList<>(trades) : List.of();
        this.matched = matched;
        this.partial = partial;
        this.matchingTimeNs = matchingTimeNs;
    }

    public static MatchResult noMatch(Order order, long matchingTimeNs) {
        return new MatchResult(order, List.of(), false, false, matchingTimeNs);
    }

    public Order getOrder() {
        return order;
    }

    public List<Trade> getTrades() {
        return Collections.unmodifiableList(trades);
    }

    public boolean isMatched() {
        return matched;
    }

    public boolean isPartial() {
        return partial;
    }

    public long getMatchingTimeNs() {
        return matchingTimeNs;
    }
}
