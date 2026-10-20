package com.bbcc.kidly.boundedcontext.member;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.member.app.AccessTokenRevocationCache;
import com.bbcc.kidly.boundedcontext.member.domain.MemberRepository;
import com.bbcc.kidly.global.eventpublisher.EventPublisher;
import com.bbcc.kidly.shared.seller.domain.SellerStatus;
import com.bbcc.kidly.shared.seller.event.SellerChangedEvent;
import com.bbcc.kidly.support.IntegrationTest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
class SellerEventListenerIntegrationTest {

    private static final LocalDateTime APPROVED_AT = LocalDateTime.of(2026, 10, 13, 9, 0);

    @Autowired
    private EventPublisher eventPublisher;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private AccessTokenRevocationCache accessTokenRevocationCache;

    @Test
    @DisplayName("판매자가 승인되면 회원에 상태 복사본을 저장하고, 늦은 변경만 반영한다")
    void copyStatus() {
        var memberId = memberRepository.save(MemberFixture.kakaoMember()).getId();

        publish(memberId, SellerStatus.ACTIVE, APPROVED_AT);
        assertThat(memberRepository.findById(memberId)).hasValueSatisfying(
            member -> assertThat(member.getSellerState().status()).isEqualTo(SellerStatus.ACTIVE));

        publish(memberId, SellerStatus.SUSPENDED, APPROVED_AT.plusDays(1));
        publish(memberId, SellerStatus.ACTIVE, APPROVED_AT);

        assertThat(memberRepository.findById(memberId)).hasValueSatisfying(member -> {
            assertThat(member.getSellerState().status()).isEqualTo(SellerStatus.SUSPENDED);
            assertThat(member.getSellerState().changedAt()).isEqualTo(APPROVED_AT.plusDays(1));
        });
    }

    @Test
    @DisplayName("활성 판매자가 정지되면 이미 발급된 Access Token을 바로 무효화한다")
    void suspendedSellerRevokesAccessToken() {
        var memberId = memberRepository.save(MemberFixture.withSellerState(SellerStatus.ACTIVE)).getId();

        publish(memberId, SellerStatus.SUSPENDED, APPROVED_AT.plusDays(1));

        assertThat(isRevoked(memberId)).isTrue();
    }

    @Test
    @DisplayName("판매자로 승인될 때는 Access Token을 무효화하지 않는다")
    void approvalDoesNotRevoke() {
        var memberId = memberRepository.save(MemberFixture.kakaoMember()).getId();

        publish(memberId, SellerStatus.ACTIVE, APPROVED_AT);

        assertThat(isRevoked(memberId)).isFalse();
    }

    @Test
    @DisplayName("무시되는 오래된 이벤트로는 Access Token을 무효화하지 않는다")
    void staleEventDoesNotRevoke() {
        var memberId = memberRepository.save(MemberFixture.withSellerState(SellerStatus.ACTIVE)).getId();

        publish(memberId, SellerStatus.SUSPENDED, MemberFixture.NOW.minusDays(1));

        assertThat(isRevoked(memberId)).isFalse();
    }

    @Test
    @DisplayName("이미 정지된 판매자에게 정지 이벤트가 다시 와도 Access Token을 무효화하지 않는다")
    void alreadySuspendedDoesNotRevoke() {
        var memberId = memberRepository.save(MemberFixture.withSellerState(SellerStatus.SUSPENDED)).getId();

        publish(memberId, SellerStatus.SUSPENDED, APPROVED_AT);

        assertThat(isRevoked(memberId)).isFalse();
    }

    private boolean isRevoked(Long memberId) {
        return accessTokenRevocationCache.isRevoked(memberId, Instant.now().minusSeconds(60));
    }

    // 리스너는 발행한 트랜잭션이 커밋된 뒤에 실행된다
    private void publish(Long sellerId, SellerStatus status, LocalDateTime occurredAt) {
        var event = new SellerChangedEvent(UUID.randomUUID(), occurredAt,
            new SellerChangedEvent.Data(sellerId, "소소한옷장", status));
        transactionTemplate.executeWithoutResult(transaction -> eventPublisher.publish(event));
    }
}
