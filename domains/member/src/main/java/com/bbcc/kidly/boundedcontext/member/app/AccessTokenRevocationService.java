package com.bbcc.kidly.boundedcontext.member.app;

import com.bbcc.kidly.global.eventpublisher.EventPublisher;
import com.bbcc.kidly.shared.member.event.MemberTokensRevokedEvent;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 회원이 지금까지 받은 Access Token을 무효화한다. member.tokens-revoked를 발행하면 Gateway가 받아 그 시각까지 발급된 토큰을 거절한다.
 * 이벤트는 커밋 뒤 바로 Kafka로 보낸다(MemberEventRelay). 롤백되면 보내지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccessTokenRevocationService {

    private final EventPublisher eventPublisher;

    // 탈퇴 · 정지 · 권한 회수 · Refresh 재사용 감지 · 판매자 정지 트랜잭션 안에서 부른다
    @Transactional(propagation = Propagation.MANDATORY)
    public void revoke(Long memberId, LocalDateTime now) {
        eventPublisher.publish(new MemberTokensRevokedEvent(
            UUID.randomUUID(), now, new MemberTokensRevokedEvent.Data(memberId, now)));
        // 감사 로그. 롤백된 무효화는 남기지 않는다
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                log.info("Access Token 무효화: memberId={}", memberId);
            }
        });
    }
}
