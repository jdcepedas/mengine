package com.mengine.gateway.web;

import com.mengine.api.OrderBookResponse;
import com.mengine.api.OrderRequest;
import com.mengine.api.OrderResponse;
import com.mengine.gateway.aeron.OrderPublisherRouter;
import com.mengine.gateway.client.MeCoreClient;
import com.mengine.gateway.config.OrderSymbolCache;
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

    private final OrderPublisherRouter orderPublisherRouter;
    private final MeCoreClient meCoreClient;
    private final TradeQuery tradeQuery;
    private final OrderSymbolCache orderSymbolCache;

    public GatewayRestController(OrderPublisherRouter orderPublisherRouter, MeCoreClient meCoreClient,
                                  TradeQuery tradeQuery, OrderSymbolCache orderSymbolCache) {
        this.orderPublisherRouter = orderPublisherRouter;
        this.meCoreClient = meCoreClient;
        this.tradeQuery = tradeQuery;
        this.orderSymbolCache = orderSymbolCache;
    }

    @PostMapping(value = "/orders", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<OrderResponse>> postOrder(@RequestBody Mono<OrderRequest> body) {
        return body
                .map(req -> {
                    if (!req.isValid()) {
                        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(OrderResponse.rejected("Invalid order"));
                    }
                    String orderId = "O" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
                    long apiReceivedAtEpochMs = System.currentTimeMillis();
                    Order order = Order.createWithApiReceivedAt(orderId, req.symbol(), req.type(), req.price(), req.quantity(), apiReceivedAtEpochMs);
                    orderSymbolCache.put(orderId, req.symbol());
                    if (!orderPublisherRouter.publish(order)) {
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

    /**
     * Return which partition (ME Core index) a symbol is routed to.
     * Use this to pick symbols that hit different partitions for testing (e.g. partition 0 vs 1).
     */
    @GetMapping(value = "/partition/{symbol}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> getPartition(@PathVariable String symbol) {
        int partitionCount = orderPublisherRouter.getPartitionCount();
        int partition = OrderPublisherRouter.partition(symbol != null ? symbol : "", partitionCount, orderPublisherRouter.getSymbols());
        return ResponseEntity.ok(Map.of(
                "symbol", symbol != null ? symbol : "",
                "partition", partition,
                "partitionCount", partitionCount
        ));
    }
}
