package com.mengine.gateway.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mengine.api.OrderBookResponse;
import com.mengine.model.Order;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * HTTP client for ME Core Query API.
 */
public class MeCoreClient {

    private final String baseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public MeCoreClient(String baseUrl) {
        this.baseUrl = baseUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public Order getOrder(String orderId) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/orders/" + orderId))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            return null;
        }
        return mapper.readValue(response.body(), Order.class);
    }

    public OrderBookResponse getOrderBook(String symbol) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/orderbook/" + symbol))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            return new OrderBookResponse(symbol, java.util.List.of(), java.util.List.of());
        }
        return mapper.readValue(response.body(), OrderBookResponse.class);
    }
}
