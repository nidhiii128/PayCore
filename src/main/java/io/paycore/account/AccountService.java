package io.paycore.account;

import io.paycore.audit.AuditAction;
import io.paycore.audit.AuditService;
import io.paycore.common.ApiException;
import io.paycore.common.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AccountService {

    private final AccountRepository accounts;
    private final AuditService audit;

    public AccountService(AccountRepository accounts, AuditService audit) {
        this.accounts = accounts;
        this.audit = audit;
    }

    @Transactional
    public AccountResponse create(UUID userId, CreateAccountRequest request) {
        if (request.type() == AccountType.SYSTEM) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "SYSTEM accounts cannot be created through the API");
        }
        Account account = accounts.save(new Account(userId, request.name().trim(), request.type()));
        audit.record(userId, AuditAction.ACCOUNT_CREATED, "ACCOUNT", account.getId(),
                Map.of("type", account.getType().name()));
        return AccountResponse.from(account);
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> list(UUID userId) {
        return accounts.findByOwnerIdOrderByCreatedAt(userId).stream().map(AccountResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public AccountResponse get(UUID userId, UUID accountId) {
        return AccountResponse.from(requireOwned(userId, accountId));
    }

    /** Accounts owned by someone else are reported as missing, so IDs cannot be probed. */
    @Transactional(readOnly = true)
    public Account requireOwned(UUID userId, UUID accountId) {
        return accounts.findByIdAndOwnerId(accountId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.ACCOUNT_NOT_FOUND, "Account not found"));
    }
}
