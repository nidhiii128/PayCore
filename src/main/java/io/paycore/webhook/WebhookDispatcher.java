package io.paycore.webhook;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Delivers due outbox rows. Safe to run on many instances at once: rows are claimed with
 * {@code FOR UPDATE SKIP LOCKED}, and claiming pushes {@code next_attempt_at} forward by a lease so no other
 * instance picks the same row while it is in flight. Delivery is at-least-once; receivers de-duplicate on event id.
 */
@Component
public class WebhookDispatcher {

    private static final Logger log = LoggerFactory.getLogger(WebhookDispatcher.class);

    private final JdbcTemplate jdbc;
    private final WebhookProperties properties;
    private final HttpClient http;

    public WebhookDispatcher(JdbcTemplate jdbc, WebhookProperties properties) {
        this.jdbc = jdbc;
        this.properties = properties;
        this.http = HttpClient.newBuilder()
                .connectTimeout(properties.requestTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    record Claimed(UUID id, int attempts, UUID eventId, String eventType, String payload, String url, String secret) {
    }

    /** @return the number of deliveries attempted */
    public int dispatchDue() {
        List<Claimed> batch = claim();
        if (batch.isEmpty()) {
            return 0;
        }
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (Claimed delivery : batch) {
                executor.submit(() -> deliver(delivery));
            }
        }
        return batch.size();
    }

    private List<Claimed> claim() {
        return jdbc.query("""
                        update webhook_deliveries d
                        set attempts = d.attempts + 1,
                            next_attempt_at = now() + (? * interval '1 millisecond')
                        from webhook_endpoints e
                        where e.id = d.endpoint_id
                          and d.id in (
                              select id from webhook_deliveries
                              where status = 'PENDING' and next_attempt_at <= now()
                              order by next_attempt_at
                              limit ?
                              for update skip locked)
                        returning d.id, d.attempts, d.event_id, d.event_type, d.payload, e.url, e.secret
                        """,
                (rs, i) -> new Claimed(rs.getObject(1, UUID.class), rs.getInt(2), rs.getObject(3, UUID.class),
                        rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7)),
                properties.lease().toMillis(), properties.batchSize());
    }

    private void deliver(Claimed delivery) {
        Integer statusCode = null;
        String error;
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(delivery.url()))
                    .timeout(properties.requestTimeout())
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "Paycore-Webhooks/1")
                    .header("Paycore-Event-Id", delivery.eventId().toString())
                    .header("Paycore-Event-Type", delivery.eventType())
                    .header(WebhookSigner.HEADER, WebhookSigner.header(delivery.secret(),
                            Instant.now().getEpochSecond(), delivery.payload()))
                    .POST(HttpRequest.BodyPublishers.ofString(delivery.payload()))
                    .build();
            HttpResponse<Void> response = http.send(request, HttpResponse.BodyHandlers.discarding());
            statusCode = response.statusCode();
            if (statusCode >= 200 && statusCode < 300) {
                markDelivered(delivery, statusCode);
                return;
            }
            error = "Endpoint responded with HTTP " + statusCode;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            error = "Interrupted";
        } catch (Exception e) {
            error = e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage());
        }
        markFailed(delivery, statusCode, error);
    }

    private void markDelivered(Claimed delivery, int statusCode) {
        jdbc.update("""
                update webhook_deliveries
                set status = 'DELIVERED', delivered_at = now(), last_status_code = ?, last_error = null
                where id = ?
                """, statusCode, delivery.id());
    }

    private void markFailed(Claimed delivery, Integer statusCode, String error) {
        String truncated = error.length() > 1000 ? error.substring(0, 1000) : error;
        if (delivery.attempts() >= properties.maxAttempts()) {
            log.warn("Webhook delivery {} is dead after {} attempts: {}", delivery.id(), delivery.attempts(), truncated);
            jdbc.update("""
                    update webhook_deliveries set status = 'DEAD', last_status_code = ?, last_error = ?
                    where id = ?
                    """, statusCode, truncated, delivery.id());
            return;
        }
        long delayMillis = properties.backoff(delivery.attempts()).toMillis();
        jdbc.update("""
                update webhook_deliveries
                set next_attempt_at = now() + (? * interval '1 millisecond'), last_status_code = ?, last_error = ?
                where id = ?
                """, delayMillis, statusCode, truncated, delivery.id());
    }
}
