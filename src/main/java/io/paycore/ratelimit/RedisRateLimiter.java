package io.paycore.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Fixed-window counter in Redis, shared by all app instances. INCR and EXPIRE run in one Lua script so a key can
 * never be left without a TTL. Fails open: if Redis is down, requests are allowed and a warning is logged,
 * because rate limiting should not take the payment API down with it.
 */
class RedisRateLimiter implements RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RedisRateLimiter.class);

    private static final RedisScript<Long> INCREMENT = RedisScript.of("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
                redis.call('EXPIRE', KEYS[1], ARGV[1])
            end
            return count
            """, Long.class);

    private final StringRedisTemplate redis;

    RedisRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public Decision tryAcquire(String key, long limit, Duration window) {
        long windowSeconds = window.toSeconds();
        long now = Instant.now().getEpochSecond();
        long windowIndex = now / windowSeconds;
        long resetSeconds = (windowIndex + 1) * windowSeconds - now;
        String redisKey = "ratelimit:" + key + ":" + windowIndex;
        try {
            Long count = redis.execute(INCREMENT, List.of(redisKey), String.valueOf(windowSeconds));
            long used = count == null ? 0 : count;
            return new Decision(used <= limit, limit, Math.max(0, limit - used), resetSeconds);
        } catch (RuntimeException e) {
            log.warn("Rate limiter unavailable, allowing request: {}", e.getMessage());
            return new Decision(true, limit, limit, resetSeconds);
        }
    }
}
