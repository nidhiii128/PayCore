package io.paycore.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
@ConditionalOnProperty(prefix = "app.rate-limit", name = "enabled", havingValue = "true")
class RateLimitConfig {

    @Bean
    RateLimiter rateLimiter(StringRedisTemplate redis) {
        return new RedisRateLimiter(redis);
    }

    @Bean
    FilterRegistrationBean<RateLimitFilter> rateLimitFilter(RateLimiter limiter, RateLimitProperties properties,
                                                            ObjectMapper mapper) {
        FilterRegistrationBean<RateLimitFilter> registration =
                new FilterRegistrationBean<>(new RateLimitFilter(limiter, properties, mapper));
        // Spring Security's chain runs at -100; run after it so the authenticated user is known.
        registration.setOrder(Ordered.LOWEST_PRECEDENCE - 10);
        return registration;
    }
}
