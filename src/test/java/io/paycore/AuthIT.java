package io.paycore;

import com.fasterxml.jackson.databind.JsonNode;
import io.paycore.support.ApiClient.User;
import io.paycore.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuthIT extends IntegrationTest {

    @Test
    void registeredUserCanLogInAndCallProtectedEndpoints() {
        User user = api.register();

        ResponseEntity<JsonNode> login = api.post(null, "/api/v1/auth/login",
                Map.of("email", user.email().toUpperCase(), "password", "correct-horse-battery"));
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        String token = login.getBody().get("accessToken").asText();

        User loggedIn = new User(user.id(), user.email(), token);
        assertThat(api.get(loggedIn, "/api/v1/accounts").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void wrongPasswordIsRejectedAndAudited() {
        User user = api.register();

        ResponseEntity<JsonNode> login = api.post(null, "/api/v1/auth/login",
                Map.of("email", user.email(), "password", "wrong-password"));
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(login.getBody().get("code").asText()).isEqualTo("INVALID_CREDENTIALS");

        JsonNode logs = api.get(user, "/api/v1/audit-logs").getBody().get("content");
        assertThat(logs.findValuesAsText("action")).contains("LOGIN_FAILED", "USER_REGISTERED");
    }

    @Test
    void unknownEmailGetsTheSameErrorAsWrongPassword() {
        ResponseEntity<JsonNode> login = api.post(null, "/api/v1/auth/login",
                Map.of("email", "nobody@example.com", "password", "whatever-password"));
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(login.getBody().get("code").asText()).isEqualTo("INVALID_CREDENTIALS");
    }

    @Test
    void duplicateEmailIsRejected() {
        User user = api.register();
        ResponseEntity<JsonNode> again = api.post(null, "/api/v1/auth/register",
                Map.of("email", user.email(), "password", "another-password", "fullName", "Someone"));
        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(again.getBody().get("code").asText()).isEqualTo("EMAIL_ALREADY_REGISTERED");
    }

    @Test
    void invalidRegistrationReturnsFieldErrors() {
        ResponseEntity<JsonNode> response = api.post(null, "/api/v1/auth/register",
                Map.of("email", "not-an-email", "password", "short", "fullName", ""));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("code").asText()).isEqualTo("VALIDATION_FAILED");
        assertThat(response.getBody().get("errors").fieldNames()).toIterable()
                .contains("email", "password", "fullName");
    }

    @Test
    void requestsWithoutAValidTokenAreUnauthorized() {
        assertThat(api.get(null, "/api/v1/accounts").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        User forged = new User(null, "x", "not.a.jwt");
        assertThat(api.get(forged, "/api/v1/accounts").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
