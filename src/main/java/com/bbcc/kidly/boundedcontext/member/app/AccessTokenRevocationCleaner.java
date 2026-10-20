package com.bbcc.kidly.boundedcontext.member.app;

import com.bbcc.kidly.boundedcontext.member.domain.AccessTokenRevocationRepository;
import com.bbcc.kidly.global.security.JwtProperties;
import java.time.Instant;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Access Token 유효 기간(+ 시계 오차)이 지난 무효화 기록을 10분마다 지운다. 그 전에 발급된 토큰은 이미 만료돼 기록이 필요 없다.
 * 캐시도 유효 기간이 지난 기록은 보지 않으므로 정리가 늦어도 동작은 같다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccessTokenRevocationCleaner {

    private final AccessTokenRevocationRepository repository;
    private final AccessTokenRevocationCache cache;
    private final JwtProperties jwtProperties;

    @Scheduled(fixedDelayString = "PT10M")
    public void deleteExpired() {
        var deleted = repository.deleteRevokedBefore(LocalDateTime.now().minus(jwtProperties.revocationRetention()));
        cache.evictExpired(Instant.now());
        // 10분마다 도는 작업이라 지운 게 있을 때만 남긴다
        if (deleted > 0) {
            log.info("만료된 Access Token 무효화 기록 {}개를 지웠습니다", deleted);
        }
    }
}
