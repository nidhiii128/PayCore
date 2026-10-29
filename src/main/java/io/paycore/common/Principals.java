package io.paycore.common;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

public final class Principals {

    private Principals() {
    }

    public static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
