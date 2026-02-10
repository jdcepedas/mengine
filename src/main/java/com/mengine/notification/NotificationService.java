package com.mengine.notification;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Notification service with premium (real-time) and standard (delayed) tiers.
 * Premium: immediate delivery via dedicated thread.
 * Standard: batched delivery with configurable delay.
 */
public class NotificationService implements EventPublisher {

    private final CopyOnWriteArrayList<Subscription> premiumSubscribers = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Subscription> standardSubscribers = new CopyOnWriteArrayList<>();
    private final long standardDelayMs;
    private final ExecutorService premiumExecutor;
    private final ExecutorService standardExecutor;
    private final LinkedBlockingQueue<Event> standardQueue = new LinkedBlockingQueue<>();
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

    public void subscribePremium(String subscriberId, EventSubscriber subscriber) {
        premiumSubscribers.add(new Subscription(subscriberId, subscriber));
    }

    public void subscribeStandard(String subscriberId, EventSubscriber subscriber) {
        standardSubscribers.add(new Subscription(subscriberId, subscriber));
    }

    public void unsubscribe(String subscriberId) {
        premiumSubscribers.removeIf(s -> s.id.equals(subscriberId));
        standardSubscribers.removeIf(s -> s.id.equals(subscriberId));
    }

    @Override
    public void publish(Event event) {
        standardQueue.offer(event);

        for (Subscription sub : premiumSubscribers) {
            premiumExecutor.submit(() -> {
                try {
                    sub.subscriber.onEvent(event);
                } catch (Exception e) {
                    // Log but don't fail
                }
            });
        }
    }

    private void startStandardDelivery() {
        standardExecutor.submit(() -> {
            while (running.get()) {
                try {
                    Thread.sleep(standardDelayMs);
                    Event event;
                    while ((event = standardQueue.poll()) != null) {
                        Event toDeliver = event;
                        for (Subscription sub : standardSubscribers) {
                            try {
                                sub.subscriber.onEvent(toDeliver);
                            } catch (Exception e) {
                                // Log but don't fail
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

    private record Subscription(String id, EventSubscriber subscriber) {}
}
