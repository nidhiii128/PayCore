package io.paycore.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitFilterTest {

    /** In-memory stand-in for Redis: counts per key, ignoring the window. */
    static class CountingLimiter implements RateLimiter {
        final Map<String, Long> counts = new HashMap<>();

        @Override
        public Decision tryAcquire(String key, long limit, Duration window) {
            long used = counts.merge(key, 1L, Long::sum);
            return new Decision(used <= limit, limit, Math.max(0, limit - used), 42);
        }
    }

    private final CountingLimiter limiter = new CountingLimiter();
    private final RateLimitFilter filter = new RateLimitFilter(limiter,
            new RateLimitProperties(true, Duration.ofMinutes(1), 3, 2), new ObjectMapper());

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void anonymousRequestsAreLimitedPerIp() throws Exception {
        assertThat(call("10.0.0.1").getStatus()).isEqualTo(200);
        assertThat(call("10.0.0.1").getStatus()).isEqualTo(200);

        MockHttpServletResponse limited = call("10.0.0.1");
        assertThat(limited.getStatus()).isEqualTo(429);
        assertThat(limited.getHeader("Retry-After")).isEqualTo("42");
        assertThat(limited.getContentAsString()).contains("RATE_LIMITED");

        assertThat(call("10.0.0.2").getStatus()).isEqualTo(200);
    }

    @Test
    void authenticatedRequestsAreLimitedPerUserWithTheHigherLimit() throws Exception {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "HS256").subject("user-1").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        for (int i = 0; i < 3; i++) {
            assertThat(call("10.0.0.1").getStatus()).isEqualTo(200);
        }
        assertThat(call("10.0.0.9").getStatus()).isEqualTo(429);
        assertThat(limiter.counts).containsOnlyKeys("user:user-1");
    }

    @Test
    void nonApiPathsAreNotLimited() throws Exception {
        for (int i = 0; i < 5; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, new MockFilterChain());
            assertThat(response.getStatus()).isEqualTo(200);
        }
        assertThat(limiter.counts).isEmpty();
    }

    private MockHttpServletResponse call(String ip) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setRemoteAddr(ip);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
