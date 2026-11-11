package com.bbcc.kidly.boundedcontext.wish.in.scheduler;

import com.bbcc.kidly.boundedcontext.wish.app.config.WishExpiryProperties;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 조르기 만료 유예(kidly.order.wish.expiry.grace, #1008)가 결제 마감(kidly.order.payment-timeout)보다 짧으면 기동 때 WARN 한 줄을
 * 남긴다(검증만, 기동은 막지 않는다). 유예가 짧으면 기한 전에 만든 주문의 결제 완료가 만료 뒤에 와 조르기와 이어지지 않을 수 있다.
 * 조르기는 주문 코드를 참조하지 않으므로 결제 마감은 설정 값으로만 읽는다(없거나 읽을 수 없으면 확인하지 않는다).
 * 만료가 꺼져 있으면 확인하지 않는다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "kidly.order.wish.enabled", havingValue = "true", matchIfMissing = true)
public class WishExpiryGraceCheck {

    private final WishExpiryProperties properties;
    private final String paymentTimeout;

    public WishExpiryGraceCheck(WishExpiryProperties properties,
        @Value("${kidly.order.payment-timeout:}") String paymentTimeout) {
        this.properties = properties;
        this.paymentTimeout = paymentTimeout;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (graceShorterThanPaymentTimeout()) {
            log.warn("조르기 만료 유예가 결제 마감보다 짧습니다: grace={} paymentTimeout={}", properties.grace(),
                paymentTimeout);
        }
    }

    boolean graceShorterThanPaymentTimeout() {
        if (!properties.enabled() || paymentTimeout == null || paymentTimeout.isBlank()) {
            return false;
        }
        try {
            Duration timeout = DurationStyle.detectAndParse(paymentTimeout.trim());
            return properties.grace().compareTo(timeout) < 0;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
