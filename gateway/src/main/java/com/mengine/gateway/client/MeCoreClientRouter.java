package com.mengine.gateway.client;

import com.mengine.api.OrderBookResponse;
import com.mengine.gateway.config.OrderSymbolCache;
import com.mengine.model.Order;

import java.util.ArrayList;
import java.util.List;

/**
 * Routes GET /orderbook and GET /orders to the ME Core partition that owns the symbol.
 */
public class MeCoreClientRouter extends MeCoreClient {

    private final List<MeCoreClient> clients;
    private final OrderSymbolCache orderSymbolCache;
    private final List<String> symbols;

    public MeCoreClientRouter(List<String> meCoreUrls, OrderSymbolCache orderSymbolCache) {
        this(meCoreUrls, orderSymbolCache, List.of());
    }

    public MeCoreClientRouter(List<String> meCoreUrls, OrderSymbolCache orderSymbolCache, List<String> symbols) {
        super(meCoreUrls.isEmpty() ? "http://localhost:8081" : meCoreUrls.get(0));
        this.clients = new ArrayList<>();
        for (String url : meCoreUrls) {
            clients.add(new MeCoreClient(url));
        }
        this.orderSymbolCache = orderSymbolCache;
        this.symbols = symbols != null ? symbols : List.of();
    }

    private MeCoreClient clientForSymbol(String symbol) {
        if (clients.isEmpty()) return null;
        int p = com.mengine.gateway.aeron.OrderPublisherRouter.partition(symbol, clients.size(), symbols);
        return clients.get(p);
    }

    @Override
    public Order getOrder(String orderId) throws Exception {
        String symbol = orderSymbolCache.get(orderId);
        if (symbol != null) {
            MeCoreClient client = clientForSymbol(symbol);
            if (client != null) return client.getOrder(orderId);
        }
        for (MeCoreClient client : clients) {
            Order order = client.getOrder(orderId);
            if (order != null) return order;
        }
        return null;
    }

    @Override
    public OrderBookResponse getOrderBook(String symbol) throws Exception {
        MeCoreClient client = clientForSymbol(symbol);
        if (client != null) return client.getOrderBook(symbol);
        return new OrderBookResponse(symbol, List.of(), List.of());
    }
}
