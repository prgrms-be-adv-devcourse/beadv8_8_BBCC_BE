package com.bbcc.kidly.boundedcontext.payment.out;

import com.bbcc.kidly.boundedcontext.payment.domain.Wallet;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletRepository;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface JpaWalletRepository extends JpaRepository<Wallet, Long>, WalletRepository {

    @Override
    @Transactional
    @Modifying
    @Query(value = """
        INSERT INTO payment.wallet (member_id, balance, created_at, updated_at)
        VALUES (:memberId, 0, :now, :now)
        ON CONFLICT (member_id) DO NOTHING
        """, nativeQuery = true)
    void createIfAbsent(Long memberId, LocalDateTime now);

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Wallet w where w.memberId = :memberId")
    Optional<Wallet> findWithLockByMemberId(Long memberId);

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Wallet w where w.id = :id")
    Optional<Wallet> findWithLockById(Long id);
}
