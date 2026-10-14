package com.bbcc.kidly.boundedcontext.seller;

import static com.bbcc.kidly.boundedcontext.seller.SellerFixture.NOW;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.seller.domain.SellerApplicationRepository;
import com.bbcc.kidly.global.eventpublisher.EventPublisher;
import com.bbcc.kidly.shared.member.event.MemberNicknameChangedEvent;
import com.bbcc.kidly.support.IntegrationTest;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
class MemberEventListenerIntegrationTest {

    @Autowired
    private EventPublisher eventPublisher;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private SellerApplicationRepository sellerApplicationRepository;

    @Test
    @DisplayName("회원이 닉네임을 바꾸면 그 회원의 신청 닉네임을 바꾸고, 늦은 변경만 반영한다")
    void changeApplicantNickname() {
        // 회원당 심사 중인 신청은 1건만 있을 수 있어서 회원 1은 거절 1건 + 심사 중 1건
        var rejected = sellerApplicationRepository.save(SellerFixture.rejectedApplication(1L)).getId();
        var pending = sellerApplicationRepository.save(SellerFixture.application(1L)).getId();
        var other = sellerApplicationRepository.save(SellerFixture.application(2L)).getId();

        publish(1L, "하은맘", NOW);

        assertThat(nicknameOf(rejected)).isEqualTo("하은맘");
        assertThat(nicknameOf(pending)).isEqualTo("하은맘");
        assertThat(nicknameOf(other)).isEqualTo(SellerFixture.APPLICANT_NICKNAME);

        publish(1L, "예전닉네임", NOW.minusDays(1));

        assertThat(nicknameOf(rejected)).isEqualTo("하은맘");
        assertThat(nicknameOf(pending)).isEqualTo("하은맘");
    }

    private String nicknameOf(Long applicationId) {
        return sellerApplicationRepository.getById(applicationId).getApplicantNickname();
    }

    // 리스너는 발행한 트랜잭션이 커밋된 뒤에 실행된다
    private void publish(Long memberId, String nickname, LocalDateTime occurredAt) {
        var event = new MemberNicknameChangedEvent(UUID.randomUUID(), occurredAt,
            new MemberNicknameChangedEvent.Data(memberId, nickname));
        transactionTemplate.executeWithoutResult(transaction -> eventPublisher.publish(event));
    }
}
