package io.paycore.support;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Thin HTTP helper so tests read as API scenarios. Every call goes through the real server. */
public class ApiClient {

    private final TestRestTemplate rest;

    public ApiClient(TestRestTemplate rest) {
        this.rest = rest;
    }

    public record User(UUID id, String email, String token) {
    }

    public User register() {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        ResponseEntity<JsonNode> response = post(null, "/api/v1/auth/register",
                Map.of("email", email, "password", "correct-horse-battery", "fullName", "Test User"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return new User(UUID.fromString(response.getBody().get("userId").asText()), email,
                response.getBody().get("accessToken").asText());
    }

    /** Registers a user with one PERSONAL account and one wallet in {@code currency}; returns the wallet id. */
    public UUID walletFor(User user, String currency) {
        ResponseEntity<JsonNode> account = post(user, "/api/v1/accounts", Map.of("name", "Main", "type", "PERSONAL"));
        assertThat(account.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String accountId = account.getBody().get("id").asText();
        ResponseEntity<JsonNode> wallet = post(user, "/api/v1/accounts/" + accountId + "/wallets",
                Map.of("currency", currency));
        assertThat(wallet.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return UUID.fromString(wallet.getBody().get("id").asText());
    }

    public ResponseEntity<JsonNode> deposit(User user, UUID walletId, long amount) {
        return postIdempotent(user, "/api/v1/wallets/" + walletId + "/deposits", UUID.randomUUID().toString(),
                Map.of("amount", amount));
    }

    public ResponseEntity<JsonNode> withdraw(User user, UUID walletId, long amount) {
        return postIdempotent(user, "/api/v1/wallets/" + walletId + "/withdrawals", UUID.randomUUID().toString(),
                Map.of("amount", amount));
    }

    public ResponseEntity<JsonNode> transfer(User user, UUID from, UUID to, long amount) {
        return transfer(user, from, to, amount, UUID.randomUUID().toString());
    }

    public ResponseEntity<JsonNode> transfer(User user, UUID from, UUID to, long amount, String idempotencyKey) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("fromWalletId", from);
        body.put("toWalletId", to);
        body.put("amount", amount);
        body.put("description", "test transfer");
        return postIdempotent(user, "/api/v1/transfers", idempotencyKey, body);
    }

    public long balance(User user, UUID walletId) {
        ResponseEntity<JsonNode> response = get(user, "/api/v1/wallets/" + walletId);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody().get("balance").asLong();
    }

    public ResponseEntity<JsonNode> get(User user, String path) {
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers(user)), JsonNode.class);
    }

    public ResponseEntity<JsonNode> post(User user, String path, Object body) {
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers(user)), JsonNode.class);
    }

    public ResponseEntity<JsonNode> postIdempotent(User user, String path, String key, Object body) {
        HttpHeaders headers = headers(user);
        headers.set("Idempotency-Key", key);
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), JsonNode.class);
    }

    private static HttpHeaders headers(User user) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (user != null) {
            headers.setBearerAuth(user.token());
        }
        return headers;
    }
}
