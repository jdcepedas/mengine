package com.mengine.core.matching;

import com.mengine.core.orderbook.OrderBook;
import com.mengine.core.orderbook.PriceLevel;
import com.mengine.model.Order;
import com.mengine.model.OrderType;
import com.mengine.model.Trade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the matching engine, especially SELL matching across multiple bid levels
 * and correct removal of empty levels.
 */
class MatchingEngineTest {

    private MatchingEngine engine;

    @BeforeEach
    void setUp() {
        engine = new MatchingEngine(5_000);
    }

    @Test
    void sellFullyFillsAcrossMultipleBidLevels_bestBidFirst() {
        String symbol = "AAPL";
        Order buyA = Order.create("A", symbol, OrderType.BUY, new BigDecimal("150"), new BigDecimal("100"));
        Order buyB = Order.create("B", symbol, OrderType.BUY, new BigDecimal("152"), new BigDecimal("50"));
        Order sellC = Order.create("C", symbol, OrderType.SELL, new BigDecimal("151"), new BigDecimal("120"));

        engine.match(buyA);
        engine.match(buyB);
        MatchResult result = engine.match(sellC);

        assertTrue(result.isMatched(), "SELL 120 @ 151 should be fully matched");
        assertEquals(0, result.getOrder().getRemainingQuantity().compareTo(BigDecimal.ZERO));

        List<Trade> trades = result.getTrades();
        assertEquals(2, trades.size(), "Should match 50 @ 152 then 70 @ 150");

        Trade first = trades.get(0);
        assertEquals(new BigDecimal("152"), first.getPrice());
        assertEquals(new BigDecimal("50"), first.getQuantity());
        assertEquals("B", first.getBuyOrderId());
        assertEquals("C", first.getSellOrderId());

        Trade second = trades.get(1);
        assertEquals(new BigDecimal("150"), second.getPrice());
        assertEquals(new BigDecimal("70"), second.getQuantity());
        assertEquals("A", second.getBuyOrderId());
        assertEquals("C", second.getSellOrderId());

        OrderBook book = engine.getOrderBook(symbol);
        assertNotNull(book);

        List<PriceLevel> bids = book.getBids();
        assertEquals(1, bids.size(), "Only 150 level should remain (152 level removed when B fully filled)");
        assertEquals(0, bids.get(0).getPrice().compareTo(new BigDecimal("150")));
        assertEquals(0, bids.get(0).getTotalQuantity().compareTo(new BigDecimal("30")));
        assertEquals(1, bids.get(0).size());

        List<PriceLevel> asks = book.getAsks();
        assertTrue(asks.isEmpty(), "SELL C fully filled so no resting ask at 151");
    }

    @Test
    void sellPartiallyFills_thenRestRemainsOnBook() {
        String symbol = "GOOG";
        Order buy = Order.create("B1", symbol, OrderType.BUY, new BigDecimal("100"), new BigDecimal("50"));
        Order sell = Order.create("S1", symbol, OrderType.SELL, new BigDecimal("99"), new BigDecimal("200"));

        engine.match(buy);
        MatchResult result = engine.match(sell);

        assertFalse(result.isMatched());
        assertTrue(result.isPartial());
        assertEquals(1, result.getTrades().size());
        assertEquals(0, result.getTrades().get(0).getQuantity().compareTo(new BigDecimal("50")));
        assertEquals(0, result.getOrder().getRemainingQuantity().compareTo(new BigDecimal("150")));

        OrderBook book = engine.getOrderBook(symbol);
        assertEquals(0, book.getBids().size());
        assertEquals(1, book.getAsks().size());
        assertEquals(0, book.getAsks().get(0).getTotalQuantity().compareTo(new BigDecimal("150")));
    }

    @Test
    void buyFullyFillsAcrossMultipleAskLevels() {
        String symbol = "MSFT";
        Order sellLow = Order.create("S1", symbol, OrderType.SELL, new BigDecimal("100"), new BigDecimal("40"));
        Order sellHigh = Order.create("S2", symbol, OrderType.SELL, new BigDecimal("101"), new BigDecimal("60"));
        Order buy = Order.create("B1", symbol, OrderType.BUY, new BigDecimal("101"), new BigDecimal("100"));

        engine.match(sellLow);
        engine.match(sellHigh);
        MatchResult result = engine.match(buy);

        assertTrue(result.isMatched());
        assertEquals(2, result.getTrades().size());
        assertEquals(0, result.getTrades().get(0).getPrice().compareTo(new BigDecimal("100")));
        assertEquals(0, result.getTrades().get(0).getQuantity().compareTo(new BigDecimal("40")));
        assertEquals(0, result.getTrades().get(1).getPrice().compareTo(new BigDecimal("101")));
        assertEquals(0, result.getTrades().get(1).getQuantity().compareTo(new BigDecimal("60")));

        OrderBook book = engine.getOrderBook(symbol);
        assertTrue(book.getAsks().isEmpty());
        assertEquals(0, book.getBids().size());
    }
}
