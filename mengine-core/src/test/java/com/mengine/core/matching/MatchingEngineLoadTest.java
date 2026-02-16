package com.mengine.core.matching;

import com.mengine.model.Order;
import com.mengine.model.OrderType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Load test: matching engine must sustain at least 5,000 matches/min with p99 latency under 200 ms.
 * Default run is 1 minute; set system property me.loadtest.durationSec for longer (e.g. 1800 for 30 min).
 */
class MatchingEngineLoadTest {

    private static final int TARGET_MATCHES_PER_MINUTE = 5_000;
    private static final long P99_LATENCY_MS = 200;
    private static final int MATCHING_TIMEOUT_MS = 200;
    private static final String SYMBOL = "AAPL";

    @Test
    @Timeout(value = 15, unit = TimeUnit.MINUTES)
    void sustainedMatchesPerMinute_andP99LatencyUnder200ms() {
        int durationSec = Integer.getInteger("me.loadtest.durationSec", 60);
        long deadlineMs = System.currentTimeMillis() + durationSec * 1_000L;

        MatchingEngine engine = new MatchingEngine(MATCHING_TIMEOUT_MS, false);

        // Each iteration: add 5000 resting SELLs (50 levels x 100 qty-1), then 50 BUYs (qty 100 each) = 5000 matches.
        int levels = 50;
        int sellsPerLevel = 100;
        int buysPerIteration = 50;
        BigDecimal qtyOne = BigDecimal.ONE;
        BigDecimal buyQty = new BigDecimal("100");

        long totalMatches = 0;
        List<Long> latenciesNs = Collections.synchronizedList(new ArrayList<>());
        long iter = 0;

        while (System.currentTimeMillis() < deadlineMs) {
            // Resting SELLs: 50 levels, 100 orders per level, qty 1
            for (int p = 0; p < levels; p++) {
                BigDecimal price = BigDecimal.valueOf(100 + p);
                for (int i = 0; i < sellsPerLevel; i++) {
                    Order sell = Order.create("S-" + iter + "-" + p + "-" + i, SYMBOL, OrderType.SELL, price, qtyOne);
                    engine.match(sell);
                }
            }
            // Incoming BUYs: each matches 100 SELLs
            for (int b = 0; b < buysPerIteration; b++) {
                Order buy = Order.create("B-" + iter + "-" + b, SYMBOL, OrderType.BUY, BigDecimal.valueOf(149), buyQty);
                MatchResult result = engine.match(buy);
                totalMatches += result.getTrades().size();
                latenciesNs.add(result.getMatchingTimeNs());
            }
            iter++;
        }

        long elapsedMin = durationSec / 60;
        long minRequiredMatches = (elapsedMin > 0) ? TARGET_MATCHES_PER_MINUTE * elapsedMin : TARGET_MATCHES_PER_MINUTE;
        assertTrue(totalMatches >= minRequiredMatches,
                "Expected at least " + minRequiredMatches + " matches in " + durationSec + "s, got " + totalMatches);

        if (!latenciesNs.isEmpty()) {
            List<Long> sorted = new ArrayList<>(latenciesNs);
            Collections.sort(sorted);
            int p99Index = (int) Math.ceil(0.99 * sorted.size()) - 1;
            p99Index = Math.max(0, p99Index);
            long p99Ns = sorted.get(p99Index);
            long p99Ms = p99Ns / 1_000_000;
            assertTrue(p99Ms < P99_LATENCY_MS,
                    "p99 match latency must be < " + P99_LATENCY_MS + " ms, was " + p99Ms + " ms");
        }
    }
}
