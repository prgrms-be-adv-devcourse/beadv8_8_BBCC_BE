package com.bbcc.kidly.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.payment.domain.Wallet;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletRepository;
import com.bbcc.kidly.support.IntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 트랜잭션이 끝나면 DB 연결을 돌려주는 설정에서는 readOnly가 DB까지 전달되지 않는다.
 * 그래서 읽기 전용 트랜잭션에서 Hibernate가 보내는 쓰기 SQL을 애플리케이션이 막는지 실제 PostgreSQL로 확인한다.
 * 쓰기 대상은 필드가 적은 예치금 지갑을 쓰고, 결과는 트랜잭션 밖에서 DB를 직접 읽어 확인한다.
 */
@IntegrationTest
class ReadOnlyWriteGuardIntegrationTest {

    private static final long MEMBER_ID = 1L;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("읽기 전용 트랜잭션에서 UPDATE 쿼리를 보내면 예외가 나고 값은 그대로다")
    void rejectUpdateQueryInReadOnlyTransaction() {
        saveWallet(MEMBER_ID, 1_000L);

        assertThatThrownBy(() -> readOnly().executeWithoutResult(status -> entityManager
            .createQuery("update Wallet w set w.balance = 5000 where w.memberId = :memberId")
            .setParameter("memberId", MEMBER_ID)
            .executeUpdate()))
            .hasStackTraceContaining("읽기 전용 트랜잭션");
        assertThat(balanceOf(MEMBER_ID)).isEqualTo(1_000L);
    }

    @Test
    @DisplayName("읽기 전용 트랜잭션에서 새 엔티티를 저장하면 예외가 나고 저장되지 않는다")
    void rejectNewEntityInReadOnlyTransaction() {
        assertThatThrownBy(() -> readOnly().executeWithoutResult(status ->
            walletRepository.save(Wallet.create(MEMBER_ID))))
            .hasStackTraceContaining("읽기 전용 트랜잭션");
        assertThat(walletCount()).isZero();
    }

    @Test
    @DisplayName("읽기 전용 트랜잭션에서 조회는 그대로 된다")
    void allowSelectInReadOnlyTransaction() {
        saveWallet(MEMBER_ID, 1_000L);

        Long balance = readOnly().execute(status ->
            walletRepository.findByMemberId(MEMBER_ID).orElseThrow().getBalance());

        assertThat(balance).isEqualTo(1_000L);
    }

    @Test
    @DisplayName("읽기 전용이 아닌 트랜잭션의 UPDATE 쿼리는 그대로 반영된다")
    void allowUpdateQueryInReadWriteTransaction() {
        saveWallet(MEMBER_ID, 1_000L);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> entityManager
            .createQuery("update Wallet w set w.balance = 5000 where w.memberId = :memberId")
            .setParameter("memberId", MEMBER_ID)
            .executeUpdate());

        assertThat(balanceOf(MEMBER_ID)).isEqualTo(5_000L);
    }

    @Test
    @DisplayName("읽기 전용 트랜잭션 안에서 REQUIRES_NEW로 연 트랜잭션의 저장은 그대로 반영된다")
    void allowSaveInRequiresNewInsideReadOnlyTransaction() {
        readOnly().executeWithoutResult(status ->
            requiresNew().executeWithoutResult(inner -> walletRepository.save(Wallet.create(MEMBER_ID))));

        assertThat(walletCount()).isEqualTo(1L);
    }

    private TransactionTemplate readOnly() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setReadOnly(true);
        return template;
    }

    // 바깥 트랜잭션을 멈추고 읽기 전용이 아닌 새 트랜잭션을 연다
    private TransactionTemplate requiresNew() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    // 일반 트랜잭션에서 잔액을 넣은 지갑을 저장하고 커밋한다
    private void saveWallet(long memberId, long balance) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Wallet wallet = Wallet.create(memberId);
            wallet.deposit(balance);
            walletRepository.save(wallet);
        });
    }

    private long balanceOf(long memberId) {
        return jdbcTemplate.queryForObject(
            "select balance from payment.wallet where member_id = ?", Long.class, memberId);
    }

    private long walletCount() {
        return jdbcTemplate.queryForObject("select count(*) from payment.wallet", Long.class);
    }
}
