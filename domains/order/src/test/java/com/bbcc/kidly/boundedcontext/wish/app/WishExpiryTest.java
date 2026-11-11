package com.bbcc.kidly.boundedcontext.wish.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bbcc.kidly.boundedcontext.wish.app.config.WishExpiryProperties;
import com.bbcc.kidly.boundedcontext.wish.app.port.WishCart;
import com.bbcc.kidly.boundedcontext.wish.app.port.WishCatalog;
import com.bbcc.kidly.boundedcontext.wish.app.port.WishEvents;
import com.bbcc.kidly.boundedcontext.wish.app.port.WishRequestRepository;
import com.bbcc.kidly.boundedcontext.wish.domain.PrincipalType;
import com.bbcc.kidly.boundedcontext.wish.domain.WishRequest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Limit;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 조르기 만료 기한 계산(#1008): 저장하지 않고 보낸 시각 + 지금 설정 기한. 기한을 바꾸면 대기 중인 조르기에도 바로, 끄면 기한 없음.
 */
class WishExpiryTest {

    private static final LocalDateTime SENT = LocalDateTime.of(2026, 11, 1, 10, 0);

    @Test
    @DisplayName("대기 중이고 켜져 있으면 만료 기한 = 보낸 시각 + ttl, 기한 시각과 같거나 지나면 지난 것")
    void expiresAt() {
        WishExpiry expiry = expiry(true, Duration.ofDays(7));
        WishRequest request = requested();

        assertThat(expiry.expiresAt(request)).contains(SENT.plusDays(7));
        assertThat(expiry.isOverdue(request, SENT.plusDays(7).minusSeconds(1))).isFalse();
        assertThat(expiry.isOverdue(request, SENT.plusDays(7))).isTrue();
        assertThat(expiry.sentBeforeExpirable(SENT.plusDays(10))).isEqualTo(SENT.plusDays(3));
    }

    @Test
    @DisplayName("유예(grace): 기한 판정(isOverdue · expiresAt)은 그대로이고, 만료 작업은 기한 + 유예가 지나야 만료할 수 있다. "
        + "대상 조회 기준도 지금 - ttl - 유예")
    void graceDelaysExpiryOnly() {
        WishExpiry expiry = new WishExpiry(new WishExpiryProperties(true, Duration.ofDays(7), Duration.ofHours(1),
            Duration.ofMinutes(15)));
        WishRequest request = requested();
        LocalDateTime deadline = SENT.plusDays(7);

        assertThat(expiry.expiresAt(request)).contains(deadline);
        assertThat(expiry.isOverdue(request, deadline.plusMinutes(5))).isTrue();
        assertThat(expiry.isExpirable(request, deadline.plusMinutes(5))).isFalse();
        assertThat(expiry.isExpirable(request, deadline.plusMinutes(15).minusSeconds(1))).isFalse();
        assertThat(expiry.isExpirable(request, deadline.plusMinutes(15))).isTrue();
        assertThat(expiry.reservationValidUntil(request)).isEqualTo(deadline);
        assertThat(expiry.sentBeforeExpirable(SENT.plusDays(10))).isEqualTo(SENT.plusDays(3).minusMinutes(15));
    }

    @Test
    @DisplayName("기한을 짧게 바꾸면 같은 대기 조르기도 바로 지난 것이 된다(저장하지 않으므로)")
    void ttlChangeAppliesToWaiting() {
        WishRequest request = requested();
        LocalDateTime now = SENT.plusMinutes(3);

        assertThat(expiry(true, Duration.ofDays(7)).isOverdue(request, now)).isFalse();
        assertThat(expiry(true, Duration.ofMinutes(2)).isOverdue(request, now)).isTrue();
        assertThat(expiry(true, Duration.ofMinutes(2)).expiresAt(request)).contains(SENT.plusMinutes(2));
    }

    @Test
    @DisplayName("꺼져 있으면 기한이 없고(expiresAt null) 지나지도 않는다. 대기가 아니면 기한이 없다")
    void disabledOrNotWaiting() {
        WishRequest request = requested();
        WishRequest rejected = requested();
        rejected.reject(WishRequest.RejectReason.OTHER, PrincipalType.MEMBER, 7L, SENT);

        assertThat(expiry(false, Duration.ofMinutes(2)).expiresAt(request)).isEmpty();
        assertThat(expiry(false, Duration.ofMinutes(2)).isOverdue(request, SENT.plusYears(1))).isFalse();
        assertThat(expiry(true, Duration.ofMinutes(2)).expiresAt(rejected)).isEmpty();
    }

    @Test
    @DisplayName("주기 작업 대상 조회는 지금 - ttl 이전에 보낸 대기 조르기다(기한 변경이 바로 반영). 꺼져 있으면 조회하지 않는다")
    void findTargetsUsesCurrentTtl() {
        WishRequestRepository repository = mock(WishRequestRepository.class);
        LocalDateTime now = SENT.plusDays(1);

        service(repository, expiry(true, Duration.ofMinutes(2))).findOverdueRequestIds(now, 5L, 10);
        verify(repository).findOverdueRequestIds(eq(WishRequest.Status.REQUESTED), eq(now.minusMinutes(2)), eq(5L),
            eq(Limit.of(10)));

        WishRequestRepository off = mock(WishRequestRepository.class);
        assertThat(service(off, expiry(false, Duration.ofMinutes(2))).findOverdueRequestIds(now, 0L, 10)).isEmpty();
        verifyNoInteractions(off);
    }

    @Test
    @DisplayName("꺼져 있으면 만료 처리도 하지 않는다")
    void expireDoesNothingWhenDisabled() {
        WishRequestRepository repository = mock(WishRequestRepository.class);
        WishRequest request = requested();
        when(repository.findById(any())).thenReturn(Optional.of(request));

        assertThat(service(repository, expiry(false, Duration.ofMinutes(2))).expire(1L, SENT.plusDays(30))).isFalse();
        assertThat(request.getStatus()).isEqualTo(WishRequest.Status.REQUESTED);
    }

    private static WishExpiryService service(WishRequestRepository repository, WishExpiry expiry) {
        return new WishExpiryService(repository, mock(WishCatalog.class), mock(WishCart.class), mock(WishEvents.class),
            expiry);
    }

    private static WishExpiry expiry(boolean enabled, Duration ttl) {
        return new WishExpiry(new WishExpiryProperties(enabled, ttl, Duration.ofHours(1), Duration.ZERO));
    }

    private static WishRequest requested() {
        WishRequest request = WishRequest.send(PrincipalType.CHILD, 1L, 1L, null, 100);
        ReflectionTestUtils.setField(request, "createdAt", SENT);
        return request;
    }
}
