package com.mengine.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mengine.buffer.ReactiveBuffer;
import com.mengine.model.Order;
import com.mengine.model.OrderRegistry;
import com.mengine.model.Trade;
import com.mengine.model.TradeStore;
import com.mengine.analytics.AnalyticsModule;
import com.mengine.matching.MatchingEngine;
import com.mengine.notification.EventSubscriber;
import com.mengine.notification.NotificationService;
import com.mengine.orderbook.OrderBook;
import com.mengine.orderbook.PriceLevel;
import org.glassfish.grizzly.http.Method;
import org.glassfish.grizzly.http.server.HttpHandler;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * REST API HTTP handler for the Matching Engine.
 */
public class OrderController extends HttpHandler {

    private final ReactiveBuffer buffer;
    private final MatchingEngine matchingEngine;
    private final OrderRegistry orderRegistry;
    private final TradeStore tradeStore;
    private final NotificationService notificationService;
    private final AnalyticsModule analyticsModule;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OrderController(ReactiveBuffer buffer,
                            MatchingEngine matchingEngine,
                            OrderRegistry orderRegistry,
                            TradeStore tradeStore,
                            NotificationService notificationService,
                            AnalyticsModule analyticsModule) {
        this.buffer = buffer;
        this.matchingEngine = matchingEngine;
        this.orderRegistry = orderRegistry;
        this.tradeStore = tradeStore;
        this.notificationService = notificationService;
        this.analyticsModule = analyticsModule;
    }

    @Override
    public void service(Request request, Response response) throws Exception {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String path = request.getRequestURI();
        if (path != null && path.contains("?")) {
            path = path.substring(0, path.indexOf('?'));
        }
        Method method = request.getMethod();

        try {
            if (method == Method.POST && "/orders".equals(path)) {
                handlePostOrder(request, response);
            } else if (method == Method.GET && path != null && path.startsWith("/orders/")) {
                String orderId = path.substring("/orders/".length());
                handleGetOrder(orderId, response);
            } else if (method == Method.GET && path != null && path.startsWith("/orderbook/")) {
                String symbol = path.substring("/orderbook/".length());
                handleGetOrderBook(symbol, response);
            } else if (method == Method.GET && path != null && path.startsWith("/trades/")) {
                String symbol = path.substring("/trades/".length());
                handleGetTrades(symbol, request, response);
            } else if (method == Method.POST && "/subscribe".equals(path)) {
                handleSubscribe(request, response);
            } else if (method == Method.GET && path != null && path.startsWith("/analytics/")) {
                String symbol = path.substring("/analytics/".length());
                handleGetAnalytics(symbol, response);
            } else {
                response.setStatus(404);
                response.getWriter().write("{\"error\":\"Not found\"}");
            }
        } catch (Exception e) {
            response.setStatus(500);
            response.getWriter().write("{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
        }
    }

    private void handlePostOrder(Request request, Response response) throws IOException {
        String body = readBody(request);
        OrderRequest req = objectMapper.readValue(body, OrderRequest.class);

        if (!req.isValid()) {
            response.setStatus(400);
            objectMapper.writeValue(response.getOutputStream(), OrderResponse.rejected("Invalid order"));
            return;
        }

        String orderId = matchingEngine.generateOrderId();
        Order order = Order.create(orderId, req.symbol(), req.type(), req.price(), req.quantity());

        if (!buffer.publish(order)) {
            response.setStatus(503);
            objectMapper.writeValue(response.getOutputStream(), OrderResponse.bufferFull(orderId));
            return;
        }

        response.setStatus(202);
        objectMapper.writeValue(response.getOutputStream(), OrderResponse.accepted(orderId));
    }

    private void handleGetOrder(String orderId, Response response) throws IOException {
        Order order = orderRegistry.get(orderId);
        if (order == null) {
            response.setStatus(404);
            response.getWriter().write("{\"error\":\"Order not found\"}");
            return;
        }
        objectMapper.writeValue(response.getOutputStream(), order);
    }

    private void handleGetOrderBook(String symbol, Response response) throws IOException {
        OrderBook book = matchingEngine.getOrCreateOrderBook(symbol);

        List<OrderBookResponse.PriceLevelView> bids = new ArrayList<>();
        for (PriceLevel level : book.getBids()) {
            bids.add(new OrderBookResponse.PriceLevelView(
                    level.getPrice(),
                    level.getTotalQuantity(),
                    level.size()
            ));
        }

        List<OrderBookResponse.PriceLevelView> asks = new ArrayList<>();
        for (PriceLevel level : book.getAsks()) {
            asks.add(new OrderBookResponse.PriceLevelView(
                    level.getPrice(),
                    level.getTotalQuantity(),
                    level.size()
            ));
        }

        OrderBookResponse obResponse = new OrderBookResponse(symbol, bids, asks);
        objectMapper.writeValue(response.getOutputStream(), obResponse);
    }

    private void handleGetTrades(String symbol, Request request, Response response) throws IOException {
        String limitParam = request.getParameter("limit");
        int limit = limitParam != null ? Integer.parseInt(limitParam) : 100;
        limit = Math.min(Math.max(limit, 1), 1000);

        List<Trade> trades = tradeStore.getRecent(symbol, limit);
        objectMapper.writeValue(response.getOutputStream(), trades);
    }

    private void handleSubscribe(Request request, Response response) throws IOException {
        String body = readBody(request);
        SubscribeRequest req = objectMapper.readValue(body, SubscribeRequest.class);
        if (req.subscriberId() == null || req.subscriberId().isBlank()) {
            response.setStatus(400);
            response.getWriter().write("{\"error\":\"subscriberId required\"}");
            return;
        }
        EventSubscriber subscriber = e -> {}; // Client would register callback
        if ("premium".equalsIgnoreCase(req.tier())) {
            notificationService.subscribePremium(req.subscriberId(), subscriber);
        } else {
            notificationService.subscribeStandard(req.subscriberId(), subscriber);
        }
        response.setStatus(200);
        response.getWriter().write("{\"status\":\"subscribed\"}");
    }

    private String readBody(Request request) throws IOException {
        StringBuilder sb = new StringBuilder();
        char[] buf = new char[4096];
        int n;
        while ((n = request.getReader().read(buf)) != -1) {
            sb.append(buf, 0, n);
        }
        return sb.toString();
    }

    private void handleGetAnalytics(String symbol, Response response) throws IOException {
        var report = "ALL".equals(symbol)
                ? analyticsModule.getOverallReport()
                : analyticsModule.getReport(symbol);
        objectMapper.writeValue(response.getOutputStream(), report);
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private record SubscribeRequest(String subscriberId, String tier) {}
}
