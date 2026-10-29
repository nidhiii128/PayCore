package io.paycore.ratelimit;

import java.time.Duration;

public interface RateLimiter {

    record Decision(boolean allowed, long limit, long remaining, long resetSeconds) {
    }

    Decision tryAcquire(String key, long limit, Duration window);
}
