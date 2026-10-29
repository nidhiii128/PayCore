package io.paycore;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.paycore.support.ApiClient.User;
import io.paycore.support.IntegrationTest;
import io.paycore.webhook.WebhookDispatcher;
import io.paycore.webhook.WebhookSigner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookIT extends IntegrationTest {

    @Autowired
    WebhookDispatcher dispatcher;

    @Autowired
    ObjectMapper mapper;

    record Received(String body, String signature, String eventType) {
    }

    private HttpServer receiver;
    private final List<Received> received = new CopyOnWriteArrayList<>();
    private final AtomicInteger responseStatus = new AtomicInteger(200);

    @BeforeEach
    void startReceiver() throws IOException {
        receiver = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        receiver.createContext("/hook", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            received.add(new Received(body, exchange.getRequestHeaders().getFirst(WebhookSigner.HEADER),
                    exchange.getRequestHeaders().getFirst("Paycore-Event-Type")));
            exchange.sendResponseHeaders(responseStatus.get(), -1);
            exchange.close();
        });
        receiver.start();
    }

    @AfterEach
    void stopReceiver() {
        receiver.stop(0);
    }

    private String hookUrl() {
        return "http://127.0.0.1:" + receiver.getAddress().getPort() + "/hook";
    }

    @Test
    void transferNotifiesTheRecipientWithASignedPayload() throws Exception {
        User alice = api.register();
        User bob = api.register();
        UUID from = api.walletFor(alice, "USD");
        UUID to = api.walletFor(bob, "USD");
        String secret = api.post(bob, "/api/v1/webhooks",
                Map.of("url", hookUrl(), "eventTypes", List.of("transfer.received"))).getBody().get("secret").asText();
        api.deposit(alice, from, 1_000);

        String txId = api.transfer(alice, from, to, 250).getBody().get("id").asText();
        dispatchAll();

        assertThat(received).hasSize(1);
        Received hook = received.getFirst();
        assertThat(hook.eventType()).isEqualTo("transfer.received");
        assertThat(WebhookSigner.verify(secret, hook.signature(), hook.body())).isTrue();
        assertThat(WebhookSigner.verify("wrong-secret", hook.signature(), hook.body())).isFalse();

        JsonNode event = mapper.readTree(hook.body());
        assertThat(event.get("type").asText()).isEqualTo("transfer.received");
        assertThat(event.get("data").get("id").asText()).isEqualTo(txId);
        assertThat(event.get("data").get("amount").asLong()).isEqualTo(250);

        String endpointId = api.get(bob, "/api/v1/webhooks").getBody().get(0).get("id").asText();
        JsonNode delivery = api.get(bob, "/api/v1/webhooks/" + endpointId + "/deliveries").getBody().get(0);
        assertThat(delivery.get("status").asText()).isEqualTo("DELIVERED");
        assertThat(delivery.get("attempts").asInt()).isEqualTo(1);
    }

    @Test
    void failingEndpointIsRetriedThenMarkedDeadAndCanBeRequeued() {
        responseStatus.set(500);
        User alice = api.register();
        UUID wallet = api.walletFor(alice, "USD");
        String endpointId = api.post(alice, "/api/v1/webhooks",
                Map.of("url", hookUrl(), "eventTypes", List.of("deposit.completed"))).getBody().get("id").asText();

        api.deposit(alice, wallet, 100);
        dispatchAll();

        // max-attempts is 3 in the test profile.
        assertThat(received).hasSize(3);
        // Every retry sends the identical event, so receivers can de-duplicate on its id.
        assertThat(received.stream().map(Received::body).distinct()).hasSize(1);
        JsonNode delivery = api.get(alice, "/api/v1/webhooks/" + endpointId + "/deliveries").getBody().get(0);
        assertThat(delivery.get("status").asText()).isEqualTo("DEAD");
        assertThat(delivery.get("lastStatusCode").asInt()).isEqualTo(500);

        responseStatus.set(204);
        String deliveryId = delivery.get("id").asText();
        ResponseEntity<JsonNode> retry = api.post(alice,
                "/api/v1/webhooks/" + endpointId + "/deliveries/" + deliveryId + "/retry", null);
        assertThat(retry.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        dispatchAll();

        assertThat(received).hasSize(4);
        assertThat(api.get(alice, "/api/v1/webhooks/" + endpointId + "/deliveries").getBody().get(0)
                .get("status").asText()).isEqualTo("DELIVERED");
    }

    @Test
    void failedMoneyMovementPublishesNothing() {
        User alice = api.register();
        User bob = api.register();
        UUID from = api.walletFor(alice, "USD");
        UUID to = api.walletFor(bob, "USD");
        api.post(bob, "/api/v1/webhooks", Map.of("url", hookUrl(), "eventTypes", List.of("transfer.received")));

        assertThat(api.transfer(alice, from, to, 1).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        dispatchAll();

        assertThat(received).isEmpty();
    }

    @Test
    void unknownEventTypesAreRejected() {
        User alice = api.register();
        ResponseEntity<JsonNode> response = api.post(alice, "/api/v1/webhooks",
                Map.of("url", hookUrl(), "eventTypes", List.of("everything")));
        assertThat(response.getBody().get("code").asText()).isEqualTo("INVALID_EVENT_TYPE");
    }

    private void dispatchAll() {
        for (int i = 0; i < 20 && dispatcher.dispatchDue() > 0; i++) {
            // Each round claims what is due; with zero backoff in tests, retries are due immediately.
        }
    }
}
