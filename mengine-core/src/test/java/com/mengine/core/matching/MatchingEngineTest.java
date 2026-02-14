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
 * Tests for the matching engine: overlap rule (trade when Bid >= Ask) and passive-order price.
 */
class MatchingEngineTest {

    private MatchingEngine engine;

    @BeforeEach
    void setUp() {
        engine = new MatchingEngine(5_000);
    }

    @Test
    void sellMatchesOnlyOverlappingBids_passivePrice() {
        String symbol = "AAPL";
        Order buyA = Order.create("A", symbol, OrderType.BUY, new BigDecimal("150"), new BigDecimal("100"));
        Order buyB = Order.create("B", symbol, OrderType.BUY, new BigDecimal("152"), new BigDecimal("50"));
        Order sellC = Order.create("C", symbol, OrderType.SELL, new BigDecimal("151"), new BigDecimal("120"));

        engine.match(buyA);
        engine.match(buyB);
        MatchResult result = engine.match(sellC);

        assertFalse(result.isMatched(), "SELL 151 overlaps only with bid 152; 70 remains");
        assertTrue(result.isPartial());
        assertEquals(0, result.getOrder().getRemainingQuantity().compareTo(new BigDecimal("70")));

        List<Trade> trades = result.getTrades();
        assertEquals(1, trades.size(), "Only bid 152 >= 151; one trade at passive (B) price");
        Trade trade = trades.get(0);
        assertEquals(new BigDecimal("152"), trade.getPrice());
        assertEquals(new BigDecimal("50"), trade.getQuantity());
        assertEquals("B", trade.getBuyOrderId());
        assertEquals("C", trade.getSellOrderId());

        OrderBook book = engine.getOrderBook(symbol);
        assertNotNull(book);
        List<PriceLevel> bids = book.getBids();
        assertEquals(1, bids.size(), "152 level removed (B filled); 150 level unchanged");
        assertEquals(0, bids.get(0).getPrice().compareTo(new BigDecimal("150")));
        assertEquals(0, bids.get(0).getTotalQuantity().compareTo(new BigDecimal("100")));
        List<PriceLevel> asks = book.getAsks();
        assertEquals(1, asks.size());
        assertEquals(0, asks.get(0).getPrice().compareTo(new BigDecimal("151")));
        assertEquals(0, asks.get(0).getTotalQuantity().compareTo(new BigDecimal("70")));
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

    @Test
    void buyExactMatch_tradeAtAskPrice() {
        String symbol = "SYM";
        Order sell = Order.create("S1", symbol, OrderType.SELL, new BigDecimal("105"), new BigDecimal("10"));
        Order buy = Order.create("B1", symbol, OrderType.BUY, new BigDecimal("105"), new BigDecimal("10"));
        engine.match(sell);
        MatchResult result = engine.match(buy);
        assertTrue(result.isMatched());
        assertEquals(1, result.getTrades().size());
        assertEquals(new BigDecimal("105"), result.getTrades().get(0).getPrice());
        assertEquals(new BigDecimal("10"), result.getTrades().get(0).getQuantity());
        assertTrue(engine.getOrderBook(symbol).getAsks().isEmpty());
        assertEquals(0, engine.getOrderBook(symbol).getBids().size());
    }

    @Test
    void buyCrossesSpread_tradeAtAskPrice() {
        String symbol = "SYM";
        Order sell = Order.create("S1", symbol, OrderType.SELL, new BigDecimal("105"), new BigDecimal("10"));
        Order buy = Order.create("B1", symbol, OrderType.BUY, new BigDecimal("110"), new BigDecimal("10"));
        engine.match(sell);
        MatchResult result = engine.match(buy);
        assertTrue(result.isMatched());
        assertEquals(1, result.getTrades().size());
        assertEquals(new BigDecimal("105"), result.getTrades().get(0).getPrice(), "Trade at passive (seller) price");
        assertEquals(new BigDecimal("10"), result.getTrades().get(0).getQuantity());
        assertTrue(engine.getOrderBook(symbol).getAsks().isEmpty());
        assertEquals(0, engine.getOrderBook(symbol).getBids().size());
    }

    @Test
    void sellExactMatch_tradeAtBidPrice() {
        String symbol = "SYM";
        Order buy = Order.create("B1", symbol, OrderType.BUY, new BigDecimal("105"), new BigDecimal("10"));
        Order sell = Order.create("S1", symbol, OrderType.SELL, new BigDecimal("105"), new BigDecimal("10"));
        engine.match(buy);
        MatchResult result = engine.match(sell);
        assertTrue(result.isMatched());
        assertEquals(1, result.getTrades().size());
        assertEquals(new BigDecimal("105"), result.getTrades().get(0).getPrice());
        assertEquals(new BigDecimal("10"), result.getTrades().get(0).getQuantity());
        assertEquals(0, engine.getOrderBook(symbol).getBids().size());
        assertTrue(engine.getOrderBook(symbol).getAsks().isEmpty());
    }

    @Test
    void sellCrossesSpread_tradeAtBidPrice() {
        String symbol = "SYM";
        Order buy = Order.create("B1", symbol, OrderType.BUY, new BigDecimal("110"), new BigDecimal("10"));
        Order sell = Order.create("S1", symbol, OrderType.SELL, new BigDecimal("105"), new BigDecimal("10"));
        engine.match(buy);
        MatchResult result = engine.match(sell);
        assertTrue(result.isMatched());
        assertEquals(1, result.getTrades().size());
        assertEquals(new BigDecimal("110"), result.getTrades().get(0).getPrice(), "Trade at passive (buyer) price");
        assertEquals(new BigDecimal("10"), result.getTrades().get(0).getQuantity());
        assertEquals(0, engine.getOrderBook(symbol).getBids().size());
        assertTrue(engine.getOrderBook(symbol).getAsks().isEmpty());
    }
}
