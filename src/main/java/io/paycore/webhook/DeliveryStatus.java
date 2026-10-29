package io.paycore.webhook;

public enum DeliveryStatus {
    PENDING,
    DELIVERED,
    /** Gave up after the maximum number of attempts. Can be retried manually. */
    DEAD
}
