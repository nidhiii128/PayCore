package io.paycore.account;

import io.paycore.common.Principals;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts")
@Tag(name = "Accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Open an account for the current user")
    public AccountResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateAccountRequest request) {
        return accountService.create(Principals.userId(jwt), request);
    }

    @GetMapping
    @Operation(summary = "List the current user's accounts")
    public List<AccountResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return accountService.list(Principals.userId(jwt));
    }

    @GetMapping("/{accountId}")
    @Operation(summary = "Get one of the current user's accounts")
    public AccountResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID accountId) {
        return accountService.get(Principals.userId(jwt), accountId);
    }
}
