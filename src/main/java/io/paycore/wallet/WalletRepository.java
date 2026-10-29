package io.paycore.wallet;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WalletRepository extends JpaRepository<Wallet, UUID> {

    List<Wallet> findByAccountIdOrderByCreatedAt(UUID accountId);

    boolean existsByAccountIdAndCurrency(UUID accountId, String currency);

    /**
     * SELECT ... FOR UPDATE in primary-key order. Every transaction that touches several wallets
     * acquires their locks in the same order, so two opposing transfers cannot deadlock.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Wallet w where w.id in :ids order by w.id")
    List<Wallet> lockAll(@Param("ids") Collection<UUID> ids);

    @Query("""
            select w from Wallet w
            where w.id = :walletId
              and w.accountId in (select a.id from Account a where a.ownerId = :ownerId)
            """)
    Optional<Wallet> findOwned(@Param("walletId") UUID walletId, @Param("ownerId") UUID ownerId);

    // The queries below return scalars on purpose: loading a Wallet entity before lockAll() would put
    // an unlocked, possibly stale copy into the persistence context.

    @Query("""
            select case when count(w) > 0 then true else false end from Wallet w
            where w.id = :walletId
              and w.accountId in (select a.id from Account a where a.ownerId = :ownerId)
            """)
    boolean isOwnedBy(@Param("walletId") UUID walletId, @Param("ownerId") UUID ownerId);

    @Query("select w.currency from Wallet w where w.id = :walletId")
    Optional<String> findCurrency(@Param("walletId") UUID walletId);

    @Query("select w.id from Wallet w where w.accountId = :accountId and w.currency = :currency")
    Optional<UUID> findIdByAccountIdAndCurrency(@Param("accountId") UUID accountId, @Param("currency") String currency);

    @Query("select a.ownerId from Account a where a.id = (select w.accountId from Wallet w where w.id = :walletId)")
    Optional<UUID> findOwnerId(@Param("walletId") UUID walletId);
}
