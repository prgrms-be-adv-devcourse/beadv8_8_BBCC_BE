package com.bbcc.kidly.boundedcontext.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.payment.out.JpaWalletRepository;
import com.bbcc.kidly.global.eventpublisher.EventPublisher;
import com.bbcc.kidly.shared.member.event.MemberRegisteredEvent;
import com.bbcc.kidly.support.IntegrationTest;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
class WalletCreationIntegrationTest {

    @Autowired
    private EventPublisher eventPublisher;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JpaWalletRepository jpaWalletRepository;

    @Test
    @DisplayName("회원 가입 이벤트가 커밋되면 잔액 0인 지갑을 만든다")
    void createWallet() {
        publishInTransaction(memberRegistered(1L));

        assertThat(jpaWalletRepository.findAll()).singleElement().satisfies(wallet -> {
            assertThat(wallet.getMemberId()).isEqualTo(1L);
            assertThat(wallet.getBalance()).isZero();
        });
    }

    @Test
    @DisplayName("같은 회원의 가입 이벤트를 두 번 받아도 지갑은 하나다")
    void duplicateEvent() {
        MemberRegisteredEvent event = memberRegistered(1L);

        publishInTransaction(event);
        publishInTransaction(event);

        assertThat(jpaWalletRepository.findAll()).singleElement()
            .satisfies(wallet -> assertThat(wallet.getMemberId()).isEqualTo(1L));
    }

    private void publishInTransaction(MemberRegisteredEvent event) {
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(event));
    }

    private static MemberRegisteredEvent memberRegistered(Long memberId) {
        return new MemberRegisteredEvent(UUID.randomUUID(), LocalDateTime.now(),
            new MemberRegisteredEvent.Data(memberId));
    }
}
