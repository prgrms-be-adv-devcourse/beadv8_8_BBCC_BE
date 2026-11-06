package com.bbcc.kidly.boundedcontext.auth.app;

import com.bbcc.kidly.shared.auth.PrincipalId;
import com.bbcc.kidly.shared.auth.event.AuthTokensRevokedEvent;
import com.bbcc.kidly.shared.auth.out.MemberTokenRevoker;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccessTokenRevocationService implements MemberTokenRevoker {

    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void revoke(Long memberId, LocalDateTime revokedAt) {
        revoke(PrincipalId.member(memberId), revokedAt);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void revoke(PrincipalId principal, LocalDateTime revokedAt) {
        eventPublisher.publishEvent(new AuthTokensRevokedEvent(UUID.randomUUID(), revokedAt,
            new AuthTokensRevokedEvent.Data(principal.type(), principal.id(), revokedAt)));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void logRevoked(AuthTokensRevokedEvent event) {
        log.info("Access Token 무효화: type={} principalId={}", event.data().principalType(), event.data().principalId());
    }
}
