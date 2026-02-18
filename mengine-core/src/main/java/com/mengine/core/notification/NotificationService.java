package com.mengine.core.notification;

import com.mengine.model.Trade;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Notification service driven by Output Disruptor (trades).
 * Premium: immediate; Standard: batched with delay.
 */
public class NotificationService {

    private final CopyOnWriteArrayList<Subscription> premiumSubscribers = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Subscription> standardSubscribers = new CopyOnWriteArrayList<>();
    private final long standardDelayMs;
    private final ExecutorService premiumExecutor;
    private final ExecutorService standardExecutor;
    private final LinkedBlockingQueue<Trade> standardQueue = new LinkedBlockingQueue<>();
    private final AtomicBoolean running = new AtomicBoolean(true);

    public NotificationService(long standardDelayMs) {
        this.standardDelayMs = standardDelayMs;
        this.premiumExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "notification-premium");
            t.setDaemon(true);
            return t;
        });
        this.standardExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "notification-standard");
            t.setDaemon(true);
            return t;
        });
        startStandardDelivery();
    }

    public void subscribePremium(String subscriberId, TradeSubscriber subscriber) {
        premiumSubscribers.add(new Subscription(subscriberId, subscriber));
    }

    public void subscribeStandard(String subscriberId, TradeSubscriber subscriber) {
        standardSubscribers.add(new Subscription(subscriberId, subscriber));
    }

    /**
     * Called from Output Disruptor consumer when a trade is available.
     */
    public void deliverTrade(Trade trade) {
        standardQueue.offer(trade);
        for (Subscription sub : premiumSubscribers) {
            premiumExecutor.submit(() -> {
                try {
                    sub.subscriber.onTrade(trade);
                } catch (Exception ignored) {
                }
            });
        }
    }

    private void startStandardDelivery() {
        standardExecutor.submit(() -> {
            while (running.get()) {
                try {
                    Thread.sleep(standardDelayMs);
                    Trade trade;
                    while ((trade = standardQueue.poll()) != null) {
                        Trade t = trade;
                        for (Subscription sub : standardSubscribers) {
                            try {
                                sub.subscriber.onTrade(t);
                            } catch (Exception ignored) {
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
    }

    public void shutdown() {
        running.set(false);
        premiumExecutor.shutdown();
        standardExecutor.shutdown();
        try {
            premiumExecutor.awaitTermination(5, TimeUnit.SECONDS);
            standardExecutor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @FunctionalInterface
    public interface TradeSubscriber {
        void onTrade(Trade trade);
    }

    private record Subscription(String id, TradeSubscriber subscriber) {}
}
