package com.bbcc.kidly.boundedcontext.member.app;

import com.bbcc.kidly.boundedcontext.member.app.port.AccessTokenRevocationCache;
import com.bbcc.kidly.boundedcontext.member.domain.AccessTokenRevocation;
import com.bbcc.kidly.boundedcontext.member.domain.AccessTokenRevocationCutoff;
import com.bbcc.kidly.boundedcontext.member.domain.AccessTokenRevocationRepository;
import com.bbcc.kidly.global.eventpublisher.EventPublisher;
import com.bbcc.kidly.global.security.AccessTokenRevocations;
import com.bbcc.kidly.global.security.JwtProperties;
import com.bbcc.kidly.shared.member.event.MemberTokensRevokedEvent;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 회원이 지금까지 받은 Access Token을 무효화하고, 요청마다 무효화된 토큰인지 알려 준다.
 * 원본은 DB이고 요청마다 보는 것은 캐시다. 캐시에는 커밋된 뒤에만 넣는다. 롤백된 정지가 멀쩡한 회원을 401로 만들지 않게 하기 위해서다.
 * 기록은 Access Token 유효 기간(+ 시계 오차)이 지나면 그 전에 발급된 토큰이 모두 만료돼 필요 없다.
 * 무효화하면 member.tokens-revoked도 발행한다. 4단계 Gateway가 이 이벤트로 같은 기준 시각을 갖는다(커밋 뒤 바로 보냄).
 */
@Slf4j
@Service
public class AccessTokenRevocationService implements AccessTokenRevocations {

    private final AccessTokenRevocationRepository repository;
    private final AccessTokenRevocationCache cache;
    private final EventPublisher eventPublisher;
    private final Duration retention;

    public AccessTokenRevocationService(AccessTokenRevocationRepository repository, AccessTokenRevocationCache cache,
        JwtProperties jwtProperties, EventPublisher eventPublisher) {
        this.repository = repository;
        this.cache = cache;
        this.eventPublisher = eventPublisher;
        this.retention = jwtProperties.revocationRetention();
    }

    // 탈퇴 · 정지 · 권한 회수 · Refresh 재사용 감지 트랜잭션 안에서 부른다
    @Transactional(propagation = Propagation.MANDATORY)
    public void revoke(Long memberId, LocalDateTime now) {
        repository.upsert(memberId, now, now);
        eventPublisher.publish(new MemberTokensRevokedEvent(
            UUID.randomUUID(), now, new MemberTokensRevokedEvent.Data(memberId, now)));
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                cache.put(memberId, AccessTokenRevocationCutoff.of(now));
                log.info("Access Token 무효화: memberId={}", memberId);
            }
        });
    }

    @Override
    public boolean isRevoked(Long memberId, Instant issuedAt) {
        return cache.find(memberId)
            .filter(cutoff -> cutoff.rejects(issuedAt, Instant.now(), retention))
            .isPresent();
    }

    // 앱이 뜰 때, 그리고 테스트가 테이블을 비운 뒤 DB 기록으로 캐시를 맞춘다
    @PostConstruct
    public void reload() {
        var cutoffs = repository.findAllRevokedAfter(LocalDateTime.now().minus(retention)).stream()
            .collect(Collectors.toMap(AccessTokenRevocation::getMemberId,
                revocation -> AccessTokenRevocationCutoff.of(revocation.getRevokedAt())));
        cache.replaceAll(cutoffs);
    }

    // 캐시도 유효 기간이 지난 기록은 보지 않으므로 정리가 늦어도 동작은 같다
    public void deleteExpired() {
        var deleted = repository.deleteRevokedBefore(LocalDateTime.now().minus(retention));
        cache.evictRevokedBefore(Instant.now().minus(retention));
        // 10분마다 도는 작업이라 지운 게 있을 때만 남긴다
        if (deleted > 0) {
            log.info("만료된 Access Token 무효화 기록 {}개를 지웠습니다", deleted);
        }
    }
}
