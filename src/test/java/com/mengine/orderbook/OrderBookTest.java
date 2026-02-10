package com.mengine.orderbook;

import com.mengine.model.Order;
import com.mengine.model.OrderType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OrderBookTest {

    @Test
    void addAndRetrieveOrders() {
        OrderBook book = new OrderBook("AAPL");
        Order buy1 = Order.create("O1", "AAPL", OrderType.BUY, new BigDecimal("100"), new BigDecimal("10"));
        Order buy2 = Order.create("O2", "AAPL", OrderType.BUY, new BigDecimal("99"), new BigDecimal("5"));
        Order sell1 = Order.create("O3", "AAPL", OrderType.SELL, new BigDecimal("101"), new BigDecimal("15"));

        book.add(buy1);
        book.add(buy2);
        book.add(sell1);

        assertEquals(new BigDecimal("100"), book.getBestBid());
        assertEquals(new BigDecimal("101"), book.getBestAsk());
        assertEquals(buy1, book.getOrder("O1"));
        assertEquals(sell1, book.getOrder("O3"));
    }

    @Test
    void removeOrder() {
        OrderBook book = new OrderBook("AAPL");
        Order buy = Order.create("O1", "AAPL", OrderType.BUY, new BigDecimal("100"), new BigDecimal("10"));
        book.add(buy);
        assertTrue(book.remove(buy));
        assertNull(book.getOrder("O1"));
        assertNull(book.getBestBid());
    }

    @Test
    void getMatchingLevelsForBuy() {
        OrderBook book = new OrderBook("AAPL");
        book.add(Order.create("S1", "AAPL", OrderType.SELL, new BigDecimal("100"), new BigDecimal("1")));
        book.add(Order.create("S2", "AAPL", OrderType.SELL, new BigDecimal("101"), new BigDecimal("1")));
        book.add(Order.create("S3", "AAPL", OrderType.SELL, new BigDecimal("99"), new BigDecimal("1")));

        Order buy = Order.create("B1", "AAPL", OrderType.BUY, new BigDecimal("100"), new BigDecimal("10"));
        var levels = book.getMatchingLevels(OrderType.BUY, buy.getPrice());
        var prices = new java.util.ArrayList<BigDecimal>();
        for (PriceLevel level : levels) {
            prices.add(level.getPrice());
        }
        assertEquals(List.of(new BigDecimal("99"), new BigDecimal("100")), prices);
    }

    @Test
    void getMatchingLevelsForSell() {
        OrderBook book = new OrderBook("AAPL");
        book.add(Order.create("B1", "AAPL", OrderType.BUY, new BigDecimal("98"), new BigDecimal("1")));
        book.add(Order.create("B2", "AAPL", OrderType.BUY, new BigDecimal("100"), new BigDecimal("1")));
        book.add(Order.create("B3", "AAPL", OrderType.BUY, new BigDecimal("99"), new BigDecimal("1")));

        Order sell = Order.create("S1", "AAPL", OrderType.SELL, new BigDecimal("99"), new BigDecimal("10"));
        var levels = book.getMatchingLevels(OrderType.SELL, sell.getPrice());
        var prices = new java.util.ArrayList<BigDecimal>();
        for (PriceLevel level : levels) {
            prices.add(level.getPrice());
        }
        assertEquals(List.of(new BigDecimal("100"), new BigDecimal("99")), prices);
    }
}
