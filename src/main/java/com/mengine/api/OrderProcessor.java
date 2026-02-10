package com.mengine.api;

import com.mengine.analytics.AnalyticsModule;
import com.mengine.matching.MatchResult;
import com.mengine.matching.MatchingEngine;
import com.mengine.model.Order;
import com.mengine.model.OrderRegistry;
import com.mengine.model.TradeStore;
import com.mengine.notification.Event;
import com.mengine.notification.NotificationService;

import java.math.BigDecimal;

/**
 * Processes orders from the buffer: matches, stores, and publishes events.
 */
public class OrderProcessor {

    private final MatchingEngine matchingEngine;
    private final OrderRegistry orderRegistry;
    private final TradeStore tradeStore;
    private final NotificationService notificationService;
    private final AnalyticsModule analyticsModule;

    public OrderProcessor(MatchingEngine matchingEngine,
                          OrderRegistry orderRegistry,
                          TradeStore tradeStore,
                          NotificationService notificationService,
                          AnalyticsModule analyticsModule) {
        this.matchingEngine = matchingEngine;
        this.orderRegistry = orderRegistry;
        this.tradeStore = tradeStore;
        this.notificationService = notificationService;
        this.analyticsModule = analyticsModule;
    }

    public void process(Order order) {
        orderRegistry.put(order);
        analyticsModule.recordOrderPlaced(order);
        notificationService.publish(Event.orderPlaced(order));

        MatchResult result = matchingEngine.match(order);

        orderRegistry.put(result.getOrder());
        BigDecimal filledQty = order.getQuantity().subtract(result.getOrder().getRemainingQuantity());
        for (var trade : result.getTrades()) {
            tradeStore.add(trade);
            analyticsModule.recordTrade(trade);
            notificationService.publish(Event.tradeExecuted(trade));
        }

        if (result.isMatched()) {
            analyticsModule.recordOrderMatched(result.getOrder(), filledQty);
            notificationService.publish(Event.orderMatched(result.getOrder(), result.getTrades()));
        } else if (result.isPartial()) {
            analyticsModule.recordOrderPartial(result.getOrder(), filledQty);
            notificationService.publish(Event.orderPartial(result.getOrder(), result.getTrades()));
        }
    }
}
