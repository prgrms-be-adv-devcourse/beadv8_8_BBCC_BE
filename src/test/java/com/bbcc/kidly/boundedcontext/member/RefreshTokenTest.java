package com.bbcc.kidly.boundedcontext.member;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.member.domain.RefreshToken;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

class RefreshTokenTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 19, 9, 0);

    @Test
    @DisplayName("원문은 URL-safe Base64 43자(256비트)이고 매번 다르다")
    void generate() {
        var token = RefreshToken.generate();

        assertThat(token).matches("[A-Za-z0-9_-]{43}");
        assertThat(RefreshToken.generate()).isNotEqualTo(token);
    }

    // "abc"의 SHA-256은 FIPS 180-2 표준 예시 값이다
    @Test
    @DisplayName("토큰 원문을 SHA-256 소문자 hex로 바꾼다")
    void hashOf() {
        assertThat(RefreshToken.hashOf("abc"))
            .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    @DisplayName("교체된 적이 없으면 재사용이 아니다")
    void notReusedWhenNeverRotated() {
        assertThat(rotatedAt(null).isReused(NOW)).isFalse();
    }

    @Test
    @DisplayName("교체 뒤 10초 안에 다시 오면 동시 재발급으로 보고 재사용이 아니다")
    void notReusedWithinGrace() {
        assertThat(rotatedAt(NOW.minusSeconds(10)).isReused(NOW)).isFalse();
    }

    @Test
    @DisplayName("교체 뒤 10초가 지나서 다시 오면 재사용이다")
    void reusedAfterGrace() {
        assertThat(rotatedAt(NOW.minusSeconds(10).minusNanos(1)).isReused(NOW)).isTrue();
    }

    // 교체 시각은 리포지토리 쿼리로만 바뀌어서 테스트에서는 필드를 직접 넣는다
    private static RefreshToken rotatedAt(LocalDateTime rotatedAt) {
        var token = BeanUtils.instantiateClass(RefreshToken.class);
        ReflectionTestUtils.setField(token, "rotatedAt", rotatedAt);
        return token;
    }
}
