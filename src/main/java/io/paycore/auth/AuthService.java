package io.paycore.auth;

import io.paycore.audit.AuditAction;
import io.paycore.audit.AuditService;
import io.paycore.common.ApiException;
import io.paycore.common.ErrorCode;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokens;
    private final AuditService audit;
    // Compared against when the email is unknown, so response time does not reveal which emails exist.
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, TokenService tokens, AuditService audit) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.audit = audit;
        this.dummyHash = passwordEncoder.encode("not-a-real-password");
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (users.existsByEmail(email)) {
            throw new ApiException(ErrorCode.EMAIL_ALREADY_REGISTERED, "Email is already registered");
        }
        User user = users.save(new User(email, passwordEncoder.encode(request.password()), request.fullName().trim()));
        audit.record(user.getId(), AuditAction.USER_REGISTERED, "USER", user.getId(), Map.of("email", email));
        return tokens.issue(user);
    }

    // Deliberately not @Transactional: a failed attempt must still leave its audit row behind.
    public AuthResponse login(LoginRequest request) {
        Optional<User> user = users.findByEmail(normalizeEmail(request.email()));
        if (user.isEmpty()) {
            passwordEncoder.matches(request.password(), dummyHash);
            throw invalidCredentials();
        }
        User found = user.get();
        if (!passwordEncoder.matches(request.password(), found.getPasswordHash())) {
            audit.record(found.getId(), AuditAction.LOGIN_FAILED, "USER", found.getId(), Map.of());
            throw invalidCredentials();
        }
        audit.record(found.getId(), AuditAction.LOGIN_SUCCEEDED, "USER", found.getId(), Map.of());
        return tokens.issue(found);
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static ApiException invalidCredentials() {
        return new ApiException(ErrorCode.INVALID_CREDENTIALS, "Invalid email or password");
    }
}
