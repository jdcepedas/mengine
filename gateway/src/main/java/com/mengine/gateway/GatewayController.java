package com.mengine.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mengine.api.OrderBookResponse;
import com.mengine.api.OrderRequest;
import com.mengine.api.OrderResponse;
import com.mengine.gateway.aeron.OrderPublisher;
import com.mengine.gateway.client.MeCoreClient;
import com.mengine.gateway.client.TradeQuery;
import com.mengine.model.Order;
import com.mengine.model.OrderType;
import org.glassfish.grizzly.http.Method;
import org.glassfish.grizzly.http.server.HttpHandler;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;

import java.util.List;
import java.util.UUID;

/**
 * HTTP handler: POST /orders -> Aeron; GET /orderbook, /orders, /trades -> ME Core or DB.
 */
public class GatewayController extends HttpHandler {

    private final OrderPublisher orderPublisher;
    private final MeCoreClient meCoreClient;
    private final TradeQuery tradeQuery;
    private final ObjectMapper mapper = new ObjectMapper();

    public GatewayController(OrderPublisher orderPublisher, MeCoreClient meCoreClient, TradeQuery tradeQuery) {
        this.orderPublisher = orderPublisher;
        this.meCoreClient = meCoreClient;
        this.tradeQuery = tradeQuery;
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
            } else {
                response.setStatus(404);
                response.getWriter().write("{\"error\":\"Not found\"}");
            }
        } catch (Exception e) {
            response.setStatus(500);
            response.getWriter().write("{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    private void handlePostOrder(Request request, Response response) throws Exception {
        String body = readBody(request);
        OrderRequest req = mapper.readValue(body, OrderRequest.class);
        if (!req.isValid()) {
            response.setStatus(400);
            mapper.writeValue(response.getOutputStream(), OrderResponse.rejected("Invalid order"));
            return;
        }
        String orderId = "O" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Order order = Order.create(orderId, req.symbol(), req.type(), req.price(), req.quantity());
        if (!orderPublisher.publish(order)) {
            response.setStatus(503);
            mapper.writeValue(response.getOutputStream(), OrderResponse.bufferFull(orderId));
            return;
        }
        response.setStatus(202);
        mapper.writeValue(response.getOutputStream(), OrderResponse.accepted(orderId));
    }

    private void handleGetOrder(String orderId, Response response) throws Exception {
        Order order = meCoreClient.getOrder(orderId);
        if (order == null) {
            response.setStatus(404);
            response.getWriter().write("{\"error\":\"Order not found\"}");
            return;
        }
        mapper.writeValue(response.getOutputStream(), order);
    }

    private void handleGetOrderBook(String symbol, Response response) throws Exception {
        OrderBookResponse ob = meCoreClient.getOrderBook(symbol);
        mapper.writeValue(response.getOutputStream(), ob);
    }

    private void handleGetTrades(String symbol, Request request, Response response) throws Exception {
        String limitParam = request.getParameter("limit");
        int limit = limitParam != null ? Integer.parseInt(limitParam) : 100;
        limit = Math.min(Math.max(limit, 1), 1000);
        List<com.mengine.model.Trade> trades = tradeQuery.findRecentBySymbol(symbol, limit);
        mapper.writeValue(response.getOutputStream(), trades);
    }

    private String readBody(Request request) throws java.io.IOException {
        StringBuilder sb = new StringBuilder();
        char[] buf = new char[4096];
        int n;
        while ((n = request.getReader().read(buf)) != -1) {
            sb.append(buf, 0, n);
        }
        return sb.toString();
    }
}
