package io.paycore.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.paycore.common.ApiException;
import io.paycore.common.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Makes a side-effecting operation safe to retry.
 *
 * <p>The key is claimed with {@code INSERT ... ON CONFLICT DO NOTHING} inside the same database transaction as the
 * operation itself. If two requests race on the same key, PostgreSQL makes the second INSERT wait until the first
 * transaction finishes. If the first committed, the second sees the stored response and replays it. If the first
 * rolled back (for example on insufficient funds), the key is free again and the second request runs normally.
 */
@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);
    private static final Pattern VALID_KEY = Pattern.compile("[\\x21-\\x7E]{1,255}");

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final IdempotencyProperties properties;

    public IdempotencyService(JdbcTemplate jdbc, ObjectMapper mapper, IdempotencyProperties properties) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.properties = properties;
    }

    private record StoredResponse(String requestHash, Integer status, String body) {
    }

    /**
     * @param operation identifies the endpoint (and target resource), so a key cannot be replayed against a
     *                  different operation
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public <T> IdempotentResponse<T> execute(UUID userId, String key, String operation, Object request,
                                             Class<T> responseType, Supplier<IdempotentResponse<T>> action) {
        if (key == null || !VALID_KEY.matcher(key).matches()) {
            throw new ApiException(ErrorCode.INVALID_IDEMPOTENCY_KEY,
                    "Idempotency-Key must be 1-255 printable ASCII characters");
        }
        String requestHash = hash(operation, request);

        int claimed = jdbc.update("""
                insert into idempotency_keys (user_id, idempotency_key, request_hash, created_at)
                values (?, ?, ?, now())
                on conflict (user_id, idempotency_key) do nothing
                """, userId, key, requestHash);

        if (claimed == 0) {
            return replay(userId, key, requestHash, responseType);
        }

        IdempotentResponse<T> response = action.get();
        jdbc.update("""
                update idempotency_keys set response_status = ?, response_body = ?
                where user_id = ? and idempotency_key = ?
                """, response.status(), toJson(response.body()), userId, key);
        return response;
    }

    private <T> IdempotentResponse<T> replay(UUID userId, String key, String requestHash, Class<T> responseType) {
        StoredResponse stored = jdbc.queryForObject("""
                        select request_hash, response_status, response_body from idempotency_keys
                        where user_id = ? and idempotency_key = ?
                        """,
                (rs, i) -> new StoredResponse(rs.getString(1), (Integer) rs.getObject(2), rs.getString(3)),
                userId, key);
        if (!stored.requestHash().equals(requestHash)) {
            throw new ApiException(ErrorCode.IDEMPOTENCY_KEY_REUSED,
                    "Idempotency-Key was already used with a different request");
        }
        try {
            return new IdempotentResponse<>(stored.status(), mapper.readValue(stored.body(), responseType), true);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored idempotent response is unreadable", e);
        }
    }

    String hash(String operation, Object request) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(operation.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) '\n');
            digest.update(mapper.writeValueAsBytes(request));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException | JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private String toJson(Object body) {
        try {
            return mapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT1M")
    public void purgeExpired() {
        Instant cutoff = Instant.now().minus(properties.retention());
        int deleted = jdbc.update("delete from idempotency_keys where created_at < ?", Timestamp.from(cutoff));
        if (deleted > 0) {
            log.info("Purged {} expired idempotency keys", deleted);
        }
    }
}
