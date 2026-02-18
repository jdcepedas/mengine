package com.mengine.core.metrics;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Simple counters for observability: match rate, dropped orders.
 * Exposed via Query API GET /metrics and used for readiness.
 */
public class MatchingMetrics {

    private final AtomicLong matchesTotal = new AtomicLong(0);
    private final AtomicLong droppedOrdersTotal = new AtomicLong(0);

    public void recordMatch() {
        matchesTotal.incrementAndGet();
    }

    public void recordMatches(int count) {
        if (count > 0) {
            matchesTotal.addAndGet(count);
        }
    }

    public void recordDroppedOrder() {
        droppedOrdersTotal.incrementAndGet();
    }

    public long getMatchesTotal() {
        return matchesTotal.get();
    }

    public long getDroppedOrdersTotal() {
        return droppedOrdersTotal.get();
    }
}
