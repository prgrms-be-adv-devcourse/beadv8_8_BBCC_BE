package com.bbcc.kidly.boundedcontext.payment;

import static com.bbcc.kidly.boundedcontext.payment.PaymentFixture.MEMBER_ID;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.payment.domain.Wallet;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletRepository;
import com.bbcc.kidly.boundedcontext.payment.out.JpaWalletRepository;
import com.bbcc.kidly.global.eventpublisher.EventPublisher;
import com.bbcc.kidly.shared.event.DomainEvent;
import com.bbcc.kidly.shared.member.event.MemberWithdrawnEvent;
import com.bbcc.kidly.shared.settlement.event.SettlementPayoutRequestedEvent;
import com.bbcc.kidly.support.IntegrationTest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * member.withdrawn 커밋 → 지갑 동결(#670). 테스트 설정은 Kafka 수신 스위치가 꺼져 있어 Spring 리스너로 받는다.
 * 리스너는 발행한 트랜잭션이 커밋된 뒤에 실행되므로 테스트에 @Transactional을 붙이지 않는다.
 */
@IntegrationTest
class WalletFreezeIntegrationTest {

    @Autowired
    private EventPublisher eventPublisher;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private JpaWalletRepository jpaWalletRepository;

    @Test
    @DisplayName("회원 탈퇴 이벤트가 커밋되면 지갑을 동결하고 잔액은 그대로 둔다")
    void freezeOnWithdrawn() {
        walletRepository.save(PaymentFixture.wallet(5_000L));

        publishInTransaction(memberWithdrawn());

        assertThat(jpaWalletRepository.findAll()).singleElement().satisfies(wallet -> {
            assertThat(wallet.isFrozen()).isTrue();
            assertThat(wallet.getBalance()).isEqualTo(5_000L);
        });
    }

    @Test
    @DisplayName("같은 회원의 탈퇴를 두 번 받아도 처음 동결한 시각을 그대로 둔다")
    void duplicateEvent() {
        // 같은 eventId를 다시 발행하면 회원 아웃박스가 막으므로, 같은 회원의 탈퇴 이벤트를 따로 두 번 보낸다
        publishInTransaction(memberWithdrawn());
        LocalDateTime frozenAt = jpaWalletRepository.findAll().getFirst().getFrozenAt();

        publishInTransaction(memberWithdrawn());

        assertThat(jpaWalletRepository.findAll()).singleElement()
            .extracting(Wallet::getFrozenAt).isEqualTo(frozenAt);
    }

    @Test
    @DisplayName("지갑이 없으면 동결된 빈 지갑을 만든다")
    void freezeWithoutWallet() {
        publishInTransaction(memberWithdrawn());

        assertThat(jpaWalletRepository.findAll()).singleElement().satisfies(wallet -> {
            assertThat(wallet.getMemberId()).isEqualTo(MEMBER_ID);
            assertThat(wallet.isFrozen()).isTrue();
            assertThat(wallet.getBalance()).isZero();
        });
    }

    @Test
    @DisplayName("동결된 지갑에도 정산 입금은 들어온다")
    void payoutIntoFrozenWallet() {
        walletRepository.save(PaymentFixture.wallet(1_000L));
        publishInTransaction(memberWithdrawn());

        publishInTransaction(new SettlementPayoutRequestedEvent(UUID.randomUUID(), LocalDateTime.now(),
            new SettlementPayoutRequestedEvent.Data(31L, MEMBER_ID, 270_000L,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31))));

        assertThat(jpaWalletRepository.findAll()).singleElement().satisfies(wallet -> {
            assertThat(wallet.isFrozen()).isTrue();
            assertThat(wallet.getBalance()).isEqualTo(271_000L);
        });
    }

    private void publishInTransaction(DomainEvent event) {
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(event));
    }

    private static MemberWithdrawnEvent memberWithdrawn() {
        return new MemberWithdrawnEvent(UUID.randomUUID(), LocalDateTime.now(),
            new MemberWithdrawnEvent.Data(MEMBER_ID));
    }
}
