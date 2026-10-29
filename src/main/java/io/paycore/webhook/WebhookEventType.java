package io.paycore.webhook;

import java.util.Set;

public final class WebhookEventType {

    public static final String TRANSFER_SENT = "transfer.sent";
    public static final String TRANSFER_RECEIVED = "transfer.received";
    public static final String DEPOSIT_COMPLETED = "deposit.completed";
    public static final String WITHDRAWAL_COMPLETED = "withdrawal.completed";

    public static final Set<String> ALL = Set.of(TRANSFER_SENT, TRANSFER_RECEIVED, DEPOSIT_COMPLETED, WITHDRAWAL_COMPLETED);

    private WebhookEventType() {
    }
}
