package io.paycore.ledger;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    List<LedgerEntry> findByTransactionId(UUID transactionId);

    @Query(value = """
            select new io.paycore.ledger.WalletEntryResponse(
                e.id, e.transactionId, t.type, e.direction, e.amount, e.currency, e.balanceAfter,
                t.description, e.createdAt)
            from LedgerEntry e join LedgerTransaction t on t.id = e.transactionId
            where e.walletId = :walletId
            order by e.seq desc
            """,
            countQuery = "select count(e) from LedgerEntry e where e.walletId = :walletId")
    Page<WalletEntryResponse> findHistory(@Param("walletId") UUID walletId, Pageable pageable);
}
