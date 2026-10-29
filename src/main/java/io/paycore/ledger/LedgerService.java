package io.paycore.ledger;

import io.paycore.common.ApiException;
import io.paycore.common.ErrorCode;
import io.paycore.wallet.Wallet;
import io.paycore.wallet.WalletRepository;
import io.paycore.wallet.WalletService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The only code path that moves money. Every posting debits one wallet and credits another by the same amount,
 * under row locks, in the caller's transaction.
 */
@Service
public class LedgerService {

    private final WalletRepository wallets;
    private final LedgerTransactionRepository transactions;
    private final LedgerEntryRepository entries;

    public LedgerService(WalletRepository wallets, LedgerTransactionRepository transactions,
                         LedgerEntryRepository entries) {
        this.wallets = wallets;
        this.transactions = transactions;
        this.entries = entries;
    }

    public record Posting(TransactionType type, UUID debitWalletId, UUID creditWalletId, long amount,
                          String description, UUID initiatedBy) {
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public TransactionResponse post(Posting posting) {
        if (posting.amount() <= 0) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Amount must be positive");
        }
        if (posting.debitWalletId().equals(posting.creditWalletId())) {
            throw new ApiException(ErrorCode.SAME_WALLET_TRANSFER, "Source and destination wallets must differ");
        }

        Map<UUID, Wallet> locked = wallets.lockAll(List.of(posting.debitWalletId(), posting.creditWalletId()))
                .stream()
                .collect(Collectors.toMap(Wallet::getId, Function.identity()));
        Wallet from = locked.get(posting.debitWalletId());
        Wallet to = locked.get(posting.creditWalletId());
        if (from == null || to == null) {
            throw WalletService.notFound();
        }
        if (!from.getCurrency().equals(to.getCurrency())) {
            throw new ApiException(ErrorCode.CURRENCY_MISMATCH,
                    "Cannot move " + from.getCurrency() + " into a " + to.getCurrency() + " wallet");
        }
        if (from.isFrozen() || to.isFrozen()) {
            throw new ApiException(ErrorCode.WALLET_FROZEN, "Wallet " + (from.isFrozen() ? from : to).getId() + " is frozen");
        }

        from.debit(posting.amount());
        to.credit(posting.amount());

        LedgerTransaction transaction = transactions.save(new LedgerTransaction(posting.type(), posting.amount(),
                from.getCurrency(), posting.description(), posting.initiatedBy()));
        entries.saveAll(List.of(
                LedgerEntry.of(transaction, from, EntryDirection.DEBIT),
                LedgerEntry.of(transaction, to, EntryDirection.CREDIT)));

        return TransactionResponse.of(transaction, from.getId(), to.getId());
    }
}
