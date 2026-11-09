package com.bbcc.kidly.boundedcontext.wish.app.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 조르기 기한 만료 설정(#1008, BEG-05). 기본값은 order.yml 한 곳에만 둔다. 등록은 common의 @ConfigurationPropertiesScan이 한다.
 * @Scheduled는 interval 키를 문자열로 읽으므로 1초 이상을 여기서 검증해 키 이름이 나오는 바인딩 오류로 기동에 실패하게 한다
 * (배송 DeliveryTrackingProperties와 같은 이유).
 *
 * @param enabled 끄면 만료 주기 작업이 뜨지 않고 만료 기한도 없다(응답 expiresAt null)
 * @param ttl 보낸 뒤 만료까지. 1초 이상. 저장하지 않고 그때그때 계산하므로 바꾸면 대기 중인 조르기에도 바로 적용된다
 * @param interval 만료 검사 주기. 1초 이상(이전 실행이 끝난 뒤부터 잰다)
 * @param grace 기한이 지난 뒤 만료 작업이 만료하기까지 더 기다리는 시간(0 이상). 기한 전에 만든 주문의 결제 완료가 늦게 와도 반영되게
 *     결제 마감(kidly.order.payment-timeout)보다 길게 둔다. 기한 판정(expiresAt · 거절/철회 409 · 예약 결제 가능 기한)은 바꾸지 않는다
 */
@Validated
@ConfigurationProperties("kidly.order.wish.expiry")
public record WishExpiryProperties(boolean enabled, @NotNull Duration ttl, @NotNull Duration interval,
    @NotNull Duration grace) {

    private static final Duration MIN = Duration.ofSeconds(1);

    @AssertTrue(message = "kidly.order.wish.expiry.ttl은 1초 이상이어야 합니다")
    public boolean isTtlAtLeastOneSecond() {
        return ttl == null || ttl.compareTo(MIN) >= 0;
    }

    @AssertTrue(message = "kidly.order.wish.expiry.grace는 0 이상이어야 합니다")
    public boolean isGraceNotNegative() {
        return grace == null || !grace.isNegative();
    }

    @AssertTrue(message = "kidly.order.wish.expiry.interval은 1초 이상이어야 합니다")
    public boolean isIntervalAtLeastOneSecond() {
        return interval == null || interval.compareTo(MIN) >= 0;
    }
}
