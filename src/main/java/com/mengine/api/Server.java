package com.mengine.api;

import com.mengine.buffer.DisruptorBuffer;
import com.mengine.buffer.ReactiveBuffer;
import com.mengine.config.EngineConfig;
import com.mengine.matching.MatchingEngine;
import com.mengine.analytics.AnalyticsModule;
import com.mengine.model.OrderRegistry;
import com.mengine.model.TradeStore;
import com.mengine.notification.NotificationService;
import org.glassfish.grizzly.http.server.HttpServer;

import java.io.IOException;

/**
 * Embedded HTTP server for the Matching Engine REST API.
 */
public class Server {

    private final EngineConfig config;
    private final HttpServer httpServer;
    private final ReactiveBuffer buffer;
    private final OrderProcessor orderProcessor;

    public Server(EngineConfig config) {
        this.config = config;

        MatchingEngine matchingEngine = new MatchingEngine(config.getMatchingTimeoutMs());
        OrderRegistry orderRegistry = new OrderRegistry();
        TradeStore tradeStore = new TradeStore(config.getTradeStoreSize());
        NotificationService notificationService = new NotificationService(config.getStandardDelayMs());
        AnalyticsModule analyticsModule = new AnalyticsModule();

        this.orderProcessor = new OrderProcessor(
                matchingEngine,
                orderRegistry,
                tradeStore,
                notificationService,
                analyticsModule
        );

        this.buffer = new DisruptorBuffer(config.getBufferSize(), orderProcessor::process);
        buffer.start();

        OrderController controller = new OrderController(
                buffer,
                matchingEngine,
                orderRegistry,
                tradeStore,
                notificationService,
                analyticsModule
        );

        this.httpServer = HttpServer.createSimpleServer(".", config.getServerPort());
        httpServer.getServerConfiguration().addHttpHandler(controller, "/");
    }

    public void start() {
        try {
            httpServer.start();
            System.out.println("Matching Engine server started on port " + config.getServerPort());
            System.out.println("Endpoints: POST /orders, GET /orders/{id}, GET /orderbook/{symbol}, GET /trades/{symbol}");
        } catch (IOException e) {
            throw new RuntimeException("Failed to start server", e);
        }
    }

    public void shutdown() {
        buffer.shutdown();
        httpServer.shutdown();
    }
}
