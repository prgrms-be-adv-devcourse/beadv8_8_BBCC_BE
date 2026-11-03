package com.bbcc.kidly.boundedcontext.auth.app;

import com.bbcc.kidly.global.eventpublisher.EventPublisher;
import com.bbcc.kidly.shared.auth.PrincipalId;
import com.bbcc.kidly.shared.auth.event.AuthTokensRevokedEvent;
import com.bbcc.kidly.shared.auth.out.MemberTokenRevoker;
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
 * 주체가 지금까지 받은 Access Token을 무효화한다. auth.tokens-revoked를 발행하면 Gateway가 받아 그 시각까지 발급된 토큰을 거절한다.
 * 이벤트는 커밋 뒤 바로 Kafka로 보낸다({@link AuthEventRelay}). 롤백되면 보내지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccessTokenRevocationService implements MemberTokenRevoker {

    private final EventPublisher eventPublisher;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void revoke(Long memberId, LocalDateTime revokedAt) {
        revoke(PrincipalId.member(memberId), revokedAt);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void revoke(PrincipalId principal, LocalDateTime revokedAt) {
        eventPublisher.publish(new AuthTokensRevokedEvent(UUID.randomUUID(), revokedAt,
            new AuthTokensRevokedEvent.Data(principal.type(), principal.id(), revokedAt)));
        // 감사 로그. 롤백된 무효화는 남기지 않는다
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                log.info("Access Token 무효화: type={} principalId={}", principal.type(), principal.id());
            }
        });
    }
}
