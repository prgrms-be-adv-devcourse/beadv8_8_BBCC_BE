package com.bbcc.kidly.boundedcontext.member;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.member.domain.AccessTokenRevocationCutoff;
import com.bbcc.kidly.boundedcontext.member.out.InMemoryAccessTokenRevocationCache;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InMemoryAccessTokenRevocationCacheTest {

    private final InMemoryAccessTokenRevocationCache cache = new InMemoryAccessTokenRevocationCache();
    private final Instant now = Instant.parse("2026-10-20T05:00:00Z");

    @Test
    @DisplayName("같은 회원을 다시 넣으면 더 늦은 기준 시각이 남는다")
    void putKeepsLater() {
        cache.put(1L, new AccessTokenRevocationCutoff(now));
        cache.put(1L, new AccessTokenRevocationCutoff(now.minusSeconds(60)));

        assertThat(cache.find(1L)).contains(new AccessTokenRevocationCutoff(now));
    }

    @Test
    @DisplayName("replaceAll하면 넘긴 기록만 남는다")
    void replaceAll() {
        cache.put(1L, new AccessTokenRevocationCutoff(now));

        cache.replaceAll(Map.of(2L, new AccessTokenRevocationCutoff(now)));

        assertThat(cache.find(1L)).isEmpty();
        assertThat(cache.find(2L)).isPresent();
    }

    @Test
    @DisplayName("기준 시각이 주어진 시각 이전인 기록만 지운다")
    void evictRevokedBefore() {
        cache.put(1L, new AccessTokenRevocationCutoff(now.minusSeconds(60)));
        cache.put(2L, new AccessTokenRevocationCutoff(now));

        cache.evictRevokedBefore(now);

        assertThat(cache.find(1L)).isEmpty();
        assertThat(cache.find(2L)).isPresent();
    }
}
