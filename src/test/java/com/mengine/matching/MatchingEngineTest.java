package com.mengine.matching;

import com.mengine.model.Order;
import com.mengine.model.OrderType;
import com.mengine.model.Trade;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MatchingEngineTest {

    @Test
    void fullMatchBuyAgainstSell() {
        MatchingEngine engine = new MatchingEngine(200);
        engine.match(Order.create("S1", "AAPL", OrderType.SELL, new BigDecimal("100"), new BigDecimal("10")));
        MatchResult result = engine.match(Order.create("B1", "AAPL", OrderType.BUY, new BigDecimal("100"), new BigDecimal("10")));

        assertTrue(result.isMatched());
        assertEquals(1, result.getTrades().size());
        Trade trade = result.getTrades().getFirst();
        assertEquals(new BigDecimal("10"), trade.getQuantity());
        assertEquals(new BigDecimal("100"), trade.getPrice());
        assertTrue(result.getOrder().getRemainingQuantity().compareTo(BigDecimal.ZERO) == 0);
    }

    @Test
    void partialMatch() {
        MatchingEngine engine = new MatchingEngine(200);
        engine.match(Order.create("S1", "AAPL", OrderType.SELL, new BigDecimal("100"), new BigDecimal("5")));
        MatchResult result = engine.match(Order.create("B1", "AAPL", OrderType.BUY, new BigDecimal("100"), new BigDecimal("10")));

        assertTrue(result.isPartial());
        assertEquals(1, result.getTrades().size());
        assertEquals(new BigDecimal("5"), result.getTrades().getFirst().getQuantity());
        assertEquals(new BigDecimal("5"), result.getOrder().getRemainingQuantity());
    }

    @Test
    void noMatchWhenPriceIncompatible() {
        MatchingEngine engine = new MatchingEngine(200);
        engine.match(Order.create("S1", "AAPL", OrderType.SELL, new BigDecimal("100"), new BigDecimal("10")));
        MatchResult result = engine.match(Order.create("B1", "AAPL", OrderType.BUY, new BigDecimal("99"), new BigDecimal("10")));

        assertFalse(result.isMatched());
        assertFalse(result.isPartial());
        assertEquals(0, result.getTrades().size());
        assertEquals(new BigDecimal("10"), result.getOrder().getRemainingQuantity());
    }

    @Test
    void priceTimePriority() {
        MatchingEngine engine = new MatchingEngine(200);
        engine.match(Order.create("S1", "AAPL", OrderType.SELL, new BigDecimal("100"), new BigDecimal("5")));
        engine.match(Order.create("S2", "AAPL", OrderType.SELL, new BigDecimal("99"), new BigDecimal("5")));
        MatchResult result = engine.match(Order.create("B1", "AAPL", OrderType.BUY, new BigDecimal("100"), new BigDecimal("5")));

        assertTrue(result.isMatched());
        assertEquals(new BigDecimal("99"), result.getTrades().getFirst().getPrice());
    }

    @Test
    void multipleMatches() {
        MatchingEngine engine = new MatchingEngine(200);
        engine.match(Order.create("S1", "AAPL", OrderType.SELL, new BigDecimal("100"), new BigDecimal("3")));
        engine.match(Order.create("S2", "AAPL", OrderType.SELL, new BigDecimal("100"), new BigDecimal("4")));
        MatchResult result = engine.match(Order.create("B1", "AAPL", OrderType.BUY, new BigDecimal("100"), new BigDecimal("5")));

        assertTrue(result.isMatched());
        assertEquals(2, result.getTrades().size());
        BigDecimal totalQty = result.getTrades().stream().map(Trade::getQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(new BigDecimal("5"), totalQty);
    }
}
