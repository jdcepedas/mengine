package com.mengine.notification;

/**
 * Interface for publishing events to subscribers.
 */
public interface EventPublisher {

    /**
     * Publish an event to all subscribers.
     */
    void publish(Event event);
}
