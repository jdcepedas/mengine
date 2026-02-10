package com.mengine.notification;

/**
 * Interface for subscribing to events.
 */
@FunctionalInterface
public interface EventSubscriber {

    /**
     * Called when an event is delivered.
     */
    void onEvent(Event event);
}
