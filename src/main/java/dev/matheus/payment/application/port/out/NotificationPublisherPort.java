package dev.matheus.payment.application.port.out;

import dev.matheus.payment.application.dto.PendingNotification;

public interface NotificationPublisherPort {

    /**
     * Publishes the notification to the broker. Implementations must throw if the broker
     * nacks, times out on confirm, or returns the message as unroutable.
     */
    void publish(PendingNotification notification);
}
