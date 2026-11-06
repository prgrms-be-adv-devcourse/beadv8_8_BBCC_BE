package com.bbcc.kidly.boundedcontext.wish.in.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.bbcc.kidly.boundedcontext.wish.app.config.WishExpiryProperties;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 조르기 만료 유예 확인(#1008): 결제 마감보다 짧을 때만 WARN 대상이고, 기동은 막지 않는다.
 */
class WishExpiryGraceCheckTest {

    @Test
    @DisplayName("유예가 결제 마감보다 짧으면 WARN 대상, 같거나 길면 아니다. 만료 꺼짐 · 결제 마감 없음 · 읽을 수 없음은 확인하지 않는다")
    void warnsOnlyWhenGraceShorter() {
        assertThat(check(true, Duration.ofMinutes(5), "PT10M").graceShorterThanPaymentTimeout()).isTrue();
        assertThat(check(true, Duration.ofMinutes(10), "PT10M").graceShorterThanPaymentTimeout()).isFalse();
        assertThat(check(true, Duration.ofMinutes(15), "PT10M").graceShorterThanPaymentTimeout()).isFalse();
        assertThat(check(false, Duration.ZERO, "PT10M").graceShorterThanPaymentTimeout()).isFalse();
        assertThat(check(true, Duration.ZERO, "").graceShorterThanPaymentTimeout()).isFalse();
        assertThat(check(true, Duration.ZERO, "not-a-duration").graceShorterThanPaymentTimeout()).isFalse();
        assertThatCode(() -> check(true, Duration.ZERO, "PT10M").onReady()).doesNotThrowAnyException();
    }

    private static WishExpiryGraceCheck check(boolean enabled, Duration grace, String paymentTimeout) {
        return new WishExpiryGraceCheck(new WishExpiryProperties(enabled, Duration.ofDays(7), Duration.ofHours(1),
            grace), paymentTimeout);
    }
}
