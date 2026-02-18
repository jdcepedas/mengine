package com.mengine.core.query;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mengine.api.OrderBookResponse;
import com.mengine.core.metrics.MatchingMetrics;
import com.mengine.core.model.OrderRegistry;
import com.mengine.core.orderbook.OrderBook;
import com.mengine.core.orderbook.PriceLevel;
import com.mengine.core.matching.MatchingEngine;
import com.mengine.model.Order;
import org.glassfish.grizzly.http.Method;
import org.glassfish.grizzly.http.server.HttpHandler;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;
import org.glassfish.grizzly.http.server.HttpServer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Minimal HTTP API for Gateway: GET /orderbook/{symbol}, GET /orders/{id}, GET /metrics, GET /ready.
 */
public class QueryApi {

    private final MatchingEngine matchingEngine;
    private final OrderRegistry orderRegistry;
    private final MatchingMetrics metrics;
    private final AtomicBoolean subscriptionReady;
    private final ObjectMapper mapper = new ObjectMapper();
    private HttpServer httpServer;

    public QueryApi(MatchingEngine matchingEngine, OrderRegistry orderRegistry) {
        this(matchingEngine, orderRegistry, null, null);
    }

    public QueryApi(MatchingEngine matchingEngine, OrderRegistry orderRegistry, MatchingMetrics metrics, AtomicBoolean subscriptionReady) {
        this.matchingEngine = matchingEngine;
        this.orderRegistry = orderRegistry;
        this.metrics = metrics;
        this.subscriptionReady = subscriptionReady;
    }

    public void start(int port) throws IOException {
        httpServer = HttpServer.createSimpleServer(".", port);
        httpServer.getServerConfiguration().addHttpHandler(new HttpHandler() {
            @Override
            public void service(Request request, Response response) throws Exception {
                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");
                String path = request.getRequestURI();
                if (path != null && path.contains("?")) {
                    path = path.substring(0, path.indexOf('?'));
                }
                Method method = request.getMethod();

                if (method == Method.GET && path != null && path.startsWith("/orderbook/")) {
                    String symbol = path.substring("/orderbook/".length());
                    handleOrderBook(symbol, response);
                } else if (method == Method.GET && path != null && path.startsWith("/orders/")) {
                    String orderId = path.substring("/orders/".length());
                    handleOrder(orderId, response);
                } else if (method == Method.GET && "/metrics".equals(path)) {
                    handleMetrics(response);
                } else if (method == Method.GET && "/ready".equals(path)) {
                    handleReady(response);
                } else {
                    response.setStatus(404);
                    response.getWriter().write("{\"error\":\"Not found\"}");
                }
            }
        }, "/");
        httpServer.start();
    }

    private void handleOrderBook(String symbol, Response response) throws IOException {
        OrderBook book = matchingEngine.getOrderBook(symbol);
        if (book == null) {
            book = matchingEngine.getOrCreateOrderBook(symbol);
        }
        List<OrderBookResponse.PriceLevelView> bids = new ArrayList<>();
        for (PriceLevel level : book.getBids()) {
            bids.add(new OrderBookResponse.PriceLevelView(level.getPrice(), level.getTotalQuantity(), level.size()));
        }
        List<OrderBookResponse.PriceLevelView> asks = new ArrayList<>();
        for (PriceLevel level : book.getAsks()) {
            asks.add(new OrderBookResponse.PriceLevelView(level.getPrice(), level.getTotalQuantity(), level.size()));
        }
        mapper.writeValue(response.getOutputStream(), new OrderBookResponse(symbol, bids, asks));
    }

    private void handleOrder(String orderId, Response response) throws IOException {
        Order order = orderRegistry.get(orderId);
        if (order == null) {
            response.setStatus(404);
            response.getWriter().write("{\"error\":\"Order not found\"}");
            return;
        }
        mapper.writeValue(response.getOutputStream(), order);
    }

    private void handleMetrics(Response response) throws IOException {
        if (metrics == null) {
            response.setStatus(200);
            response.getWriter().write("{\"matchesTotal\":0,\"droppedOrdersTotal\":0}");
            return;
        }
        response.setStatus(200);
        response.getWriter().write("{\"matchesTotal\":" + metrics.getMatchesTotal()
                + ",\"droppedOrdersTotal\":" + metrics.getDroppedOrdersTotal() + "}");
    }

    private void handleReady(Response response) throws IOException {
        boolean ready = subscriptionReady != null && subscriptionReady.get();
        if (ready) {
            response.setStatus(200);
            response.getWriter().write("{\"ready\":true}");
        } else {
            response.setStatus(503);
            response.getWriter().write("{\"ready\":false,\"reason\":\"Aeron subscription has no image\"}");
        }
    }

    public void shutdown() {
        if (httpServer != null) {
            httpServer.shutdown();
        }
    }
}
