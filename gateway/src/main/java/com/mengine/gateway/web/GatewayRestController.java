package com.mengine.gateway.web;

import com.mengine.api.OrderBookResponse;
import com.mengine.api.OrderRequest;
import com.mengine.api.OrderResponse;
import com.mengine.gateway.aeron.OrderPublisher;
import com.mengine.gateway.client.MeCoreClient;
import com.mengine.gateway.client.TradeQuery;
import com.mengine.model.Order;
import com.mengine.model.Trade;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * WebFlux REST controller: POST /orders -> Aeron; GET /orders, /orderbook, /trades -> ME Core or DB.
 * Blocking calls (MeCoreClient, TradeQuery) run on boundedElastic.
 */
@RestController
public class GatewayRestController {

    private final OrderPublisher orderPublisher;
    private final MeCoreClient meCoreClient;
    private final TradeQuery tradeQuery;

    public GatewayRestController(OrderPublisher orderPublisher, MeCoreClient meCoreClient, TradeQuery tradeQuery) {
        this.orderPublisher = orderPublisher;
        this.meCoreClient = meCoreClient;
        this.tradeQuery = tradeQuery;
    }

    @PostMapping(value = "/orders", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<OrderResponse>> postOrder(@RequestBody Mono<OrderRequest> body) {
        return body
                .map(req -> {
                    if (!req.isValid()) {
                        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(OrderResponse.rejected("Invalid order"));
                    }
                    String orderId = "O" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
                    Order order = Order.create(orderId, req.symbol(), req.type(), req.price(), req.quantity());
                    if (!orderPublisher.publish(order)) {
                        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(OrderResponse.bufferFull(orderId));
                    }
                    return ResponseEntity.status(HttpStatus.ACCEPTED).body(OrderResponse.accepted(orderId));
                })
                .onErrorResume(e -> Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(OrderResponse.rejected(e.getMessage()))));
    }

    @GetMapping(value = "/orders/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Object>> getOrder(@PathVariable String id) {
        return Mono.fromCallable(() -> meCoreClient.getOrder(id))
                .subscribeOn(Schedulers.boundedElastic())
                .map(order -> order != null
                        ? ResponseEntity.ok((Object) order)
                        : ResponseEntity.status(HttpStatus.NOT_FOUND).body((Object) Map.of("error", "Order not found")))
                .onErrorResume(e -> Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body((Object) Map.of("error", e.getMessage()))));
    }

    @GetMapping(value = "/orderbook/{symbol}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<OrderBookResponse>> getOrderBook(@PathVariable String symbol) {
        return Mono.fromCallable(() -> meCoreClient.getOrderBook(symbol))
                .subscribeOn(Schedulers.boundedElastic())
                .map(ResponseEntity::ok)
                .onErrorResume(e -> Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new OrderBookResponse(symbol, List.of(), List.of()))));
    }

    @GetMapping(value = "/trades/{symbol}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<List<Trade>>> getTrades(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "100") int limit) {
        int clampedLimit = Math.min(Math.max(limit, 1), 1000);
        return Mono.fromCallable(() -> tradeQuery.findRecentBySymbol(symbol, clampedLimit))
                .subscribeOn(Schedulers.boundedElastic())
                .map(ResponseEntity::ok)
                .onErrorResume(e -> Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(List.of())));
    }
}
