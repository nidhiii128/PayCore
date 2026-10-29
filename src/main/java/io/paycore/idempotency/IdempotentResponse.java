package io.paycore.idempotency;

/**
 * @param replayed true when the body was loaded from a previous request with the same Idempotency-Key
 */
public record IdempotentResponse<T>(int status, T body, boolean replayed) {

    public static <T> IdempotentResponse<T> created(T body) {
        return new IdempotentResponse<>(201, body, false);
    }
}
