package com.bbcc.kidly.boundedcontext.member.app;

import com.bbcc.kidly.boundedcontext.member.domain.AccessTokenRevocationCutoff;
import com.bbcc.kidly.boundedcontext.member.domain.AccessTokenRevocationRepository;
import com.bbcc.kidly.global.security.AccessTokenRevocations;
import com.bbcc.kidly.global.security.JwtProperties;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 회원별 Access Token 무효화 기준 시각의 메모리 사본. 요청마다 여기만 보고 DB는 보지 않는다.
 * 원본은 member.access_token_revocation이고, 앱이 뜰 때 최근 유효 기간 안의 기록을 읽어 온다.
 * 앱 인스턴스가 1개라는 전제다. 여러 대가 되면 다른 인스턴스의 기록을 모르므로 공유 저장소(Redis 등)로 옮긴다.
 */
@Component
public class AccessTokenRevocationCache implements AccessTokenRevocations {

    private volatile Map<Long, AccessTokenRevocationCutoff> cutoffs = new ConcurrentHashMap<>();
    private final AccessTokenRevocationRepository repository;
    private final Duration retention;

    public AccessTokenRevocationCache(AccessTokenRevocationRepository repository, JwtProperties jwtProperties) {
        this.repository = repository;
        this.retention = jwtProperties.revocationRetention();
    }

    @Override
    public boolean isRevoked(Long memberId, Instant issuedAt) {
        var cutoff = cutoffs.get(memberId);
        return cutoff != null && cutoff.rejects(issuedAt, Instant.now(), retention);
    }

    void put(Long memberId, AccessTokenRevocationCutoff cutoff) {
        cutoffs.merge(memberId, cutoff, AccessTokenRevocationCutoff::later);
    }

    // 앱이 뜰 때, 그리고 테스트가 테이블을 비운 뒤 DB 기록으로 메모리를 맞춘다.
    // 시작할 때만 부른다. 맵을 통째로 바꾸므로 동시에 들어온 put은 유실될 수 있다
    @PostConstruct
    public void reload() {
        var loaded = new ConcurrentHashMap<Long, AccessTokenRevocationCutoff>();
        repository.findAllRevokedAfter(LocalDateTime.now().minus(retention))
            .forEach(r -> loaded.put(r.getMemberId(), AccessTokenRevocationCutoff.of(r.getRevokedAt())));
        cutoffs = loaded;
    }

    void evictExpired(Instant now) {
        cutoffs.values().removeIf(cutoff -> cutoff.isExpired(now, retention));
    }
}
