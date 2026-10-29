package io.paycore.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.paycore.common.ErrorCode;
import io.paycore.common.GlobalExceptionHandler;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Registered after the Spring Security chain, so authenticated requests are limited per user and anonymous ones
 * per client IP.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimiter limiter;
    private final RateLimitProperties properties;
    private final ObjectMapper mapper;

    public RateLimitFilter(RateLimiter limiter, RateLimitProperties properties, ObjectMapper mapper) {
        this.limiter = limiter;
        this.properties = properties;
        this.mapper = mapper;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String key;
        long limit;
        if (auth instanceof JwtAuthenticationToken jwt) {
            key = "user:" + jwt.getName();
            limit = properties.authenticatedLimit();
        } else {
            key = "ip:" + request.getRemoteAddr();
            limit = properties.anonymousLimit();
        }

        RateLimiter.Decision decision = limiter.tryAcquire(key, limit, properties.window());
        response.setHeader("X-RateLimit-Limit", String.valueOf(decision.limit()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(decision.remaining()));
        response.setHeader("X-RateLimit-Reset", String.valueOf(decision.resetSeconds()));

        if (!decision.allowed()) {
            ProblemDetail problem = GlobalExceptionHandler.problem(ErrorCode.RATE_LIMITED, "Too many requests");
            response.setStatus(ErrorCode.RATE_LIMITED.status().value());
            response.setHeader("Retry-After", String.valueOf(decision.resetSeconds()));
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            mapper.writeValue(response.getOutputStream(), problem);
            return;
        }
        chain.doFilter(request, response);
    }
}
