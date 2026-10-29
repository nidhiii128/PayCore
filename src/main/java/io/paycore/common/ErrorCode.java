package io.paycore.common;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
    UNSUPPORTED_CURRENCY(HttpStatus.BAD_REQUEST),
    INVALID_IDEMPOTENCY_KEY(HttpStatus.BAD_REQUEST),
    INVALID_WEBHOOK_URL(HttpStatus.BAD_REQUEST),
    INVALID_EVENT_TYPE(HttpStatus.BAD_REQUEST),

    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED),

    ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND),
    WALLET_NOT_FOUND(HttpStatus.NOT_FOUND),
    TRANSACTION_NOT_FOUND(HttpStatus.NOT_FOUND),
    WEBHOOK_NOT_FOUND(HttpStatus.NOT_FOUND),
    DELIVERY_NOT_FOUND(HttpStatus.NOT_FOUND),

    EMAIL_ALREADY_REGISTERED(HttpStatus.CONFLICT),
    WALLET_ALREADY_EXISTS(HttpStatus.CONFLICT),
    CONCURRENT_MODIFICATION(HttpStatus.CONFLICT),
    CONFLICT(HttpStatus.CONFLICT),

    INSUFFICIENT_FUNDS(HttpStatus.UNPROCESSABLE_ENTITY),
    CURRENCY_MISMATCH(HttpStatus.UNPROCESSABLE_ENTITY),
    WALLET_FROZEN(HttpStatus.UNPROCESSABLE_ENTITY),
    SAME_WALLET_TRANSFER(HttpStatus.UNPROCESSABLE_ENTITY),
    IDEMPOTENCY_KEY_REUSED(HttpStatus.UNPROCESSABLE_ENTITY),
    DELIVERY_NOT_RETRYABLE(HttpStatus.UNPROCESSABLE_ENTITY),

    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
