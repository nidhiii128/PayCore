package io.paycore;

import com.fasterxml.jackson.databind.JsonNode;
import io.paycore.support.ApiClient.User;
import io.paycore.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

class IdempotencyIT extends IntegrationTest {

    @Test
    void retryingWithTheSameKeyReplaysTheOriginalResponse() {
        User alice = api.register();
        User bob = api.register();
        UUID from = api.walletFor(alice, "USD");
        UUID to = api.walletFor(bob, "USD");
        api.deposit(alice, from, 1_000);
        String key = UUID.randomUUID().toString();

        ResponseEntity<JsonNode> first = api.transfer(alice, from, to, 300, key);
        ResponseEntity<JsonNode> retry = api.transfer(alice, from, to, 300, key);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(retry.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(retry.getHeaders().getFirst("Idempotent-Replayed")).isEqualTo("true");
        assertThat(retry.getBody().get("id")).isEqualTo(first.getBody().get("id"));
        assertThat(api.balance(alice, from)).isEqualTo(700);
        assertThat(api.balance(bob, to)).isEqualTo(300);
    }

    @Test
    void reusingAKeyWithADifferentBodyIsRejected() {
        User alice = api.register();
        User bob = api.register();
        UUID from = api.walletFor(alice, "USD");
        UUID to = api.walletFor(bob, "USD");
        api.deposit(alice, from, 1_000);
        String key = UUID.randomUUID().toString();

        api.transfer(alice, from, to, 300, key);
        ResponseEntity<JsonNode> different = api.transfer(alice, from, to, 999, key);

        assertThat(different.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(different.getBody().get("code").asText()).isEqualTo("IDEMPOTENCY_KEY_REUSED");
        assertThat(api.balance(alice, from)).isEqualTo(700);
    }

    @Test
    void keysAreScopedPerUser() {
        User alice = api.register();
        User bob = api.register();
        UUID aliceWallet = api.walletFor(alice, "USD");
        UUID bobWallet = api.walletFor(bob, "USD");
        String shared = "same-key-" + UUID.randomUUID();

        assertThat(api.postIdempotent(alice, "/api/v1/wallets/" + aliceWallet + "/deposits", shared,
                Map.of("amount", 10)).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        ResponseEntity<JsonNode> bobs = api.postIdempotent(bob, "/api/v1/wallets/" + bobWallet + "/deposits", shared,
                Map.of("amount", 10));
        assertThat(bobs.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(bobs.getHeaders().getFirst("Idempotent-Replayed")).isEqualTo("false");
    }

    @Test
    void aFailedRequestDoesNotBurnTheKey() {
        User alice = api.register();
        User bob = api.register();
        UUID from = api.walletFor(alice, "USD");
        UUID to = api.walletFor(bob, "USD");
        String key = UUID.randomUUID().toString();

        assertThat(api.transfer(alice, from, to, 500, key).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        api.deposit(alice, from, 500);

        ResponseEntity<JsonNode> retry = api.transfer(alice, from, to, 500, key);
        assertThat(retry.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(retry.getHeaders().getFirst("Idempotent-Replayed")).isEqualTo("false");
    }

    @Test
    void moneyMovementRequiresAKey() {
        User alice = api.register();
        UUID wallet = api.walletFor(alice, "USD");

        ResponseEntity<JsonNode> missing = api.post(alice, "/api/v1/wallets/" + wallet + "/deposits",
                Map.of("amount", 10));
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<JsonNode> blank = api.postIdempotent(alice, "/api/v1/wallets/" + wallet + "/deposits",
                "has spaces", Map.of("amount", 10));
        assertThat(blank.getBody().get("code").asText()).isEqualTo("INVALID_IDEMPOTENCY_KEY");
    }

    @Test
    void concurrentRequestsWithTheSameKeyMoveMoneyExactlyOnce() throws Exception {
        User alice = api.register();
        User bob = api.register();
        UUID from = api.walletFor(alice, "USD");
        UUID to = api.walletFor(bob, "USD");
        api.deposit(alice, from, 10_000);
        String key = UUID.randomUUID().toString();
        int threads = 20;

        CountDownLatch start = new CountDownLatch(1);
        List<Future<ResponseEntity<JsonNode>>> results = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            for (int i = 0; i < threads; i++) {
                results.add(pool.submit((Callable<ResponseEntity<JsonNode>>) () -> {
                    start.await();
                    return api.transfer(alice, from, to, 1_000, key);
                }));
            }
            start.countDown();
        }

        List<String> ids = new ArrayList<>();
        long replayed = 0;
        for (Future<ResponseEntity<JsonNode>> result : results) {
            ResponseEntity<JsonNode> response = result.get();
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            ids.add(response.getBody().get("id").asText());
            if ("true".equals(response.getHeaders().getFirst("Idempotent-Replayed"))) {
                replayed++;
            }
        }
        assertThat(ids).containsOnly(ids.getFirst());
        assertThat(replayed).isEqualTo(threads - 1);
        assertThat(api.balance(alice, from)).isEqualTo(9_000);
        assertThat(api.balance(bob, to)).isEqualTo(1_000);
    }
}
