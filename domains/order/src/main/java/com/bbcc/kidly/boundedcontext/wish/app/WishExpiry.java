package com.bbcc.kidly.boundedcontext.wish.app;

import com.bbcc.kidly.boundedcontext.wish.app.config.WishExpiryProperties;
import com.bbcc.kidly.boundedcontext.wish.domain.WishRequest;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 조르기 만료 기한 계산(#1008). 기한은 저장하지 않고 보낸 시각 + 지금 설정 기한(ttl)으로 그때그때 계산한다: 기한을 바꾸면 대기 중인
 * 조르기에도 바로 적용되고, 끄면 기한이 없다. 기한 시각과 같거나 지났으면 지난 것이다.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "kidly.order.wish.enabled", havingValue = "true", matchIfMissing = true)
public class WishExpiry {

    private final WishExpiryProperties properties;

    public boolean enabled() {
        return properties.enabled();
    }

    /**
     * 대기 중인 조르기의 만료 기한. 꺼져 있거나 대기가 아니면 빈 값.
     */
    public Optional<LocalDateTime> expiresAt(WishRequest request) {
        if (!properties.enabled() || request.getStatus() != WishRequest.Status.REQUESTED
            || request.getRequestedAt() == null) {
            return Optional.empty();
        }
        return Optional.of(request.getRequestedAt().plus(properties.ttl()));
    }

    /**
     * 장바구니 예약의 결제 가능 기한으로 넘길 값(#1008): 대기 중 조르기의 만료 기한, 꺼져 있거나 대기가 아니면 null.
     * 장바구니 기한은 이 계산값의 사본이고, 조르기는 기한을 저장하지 않는다.
     */
    public LocalDateTime reservationValidUntil(WishRequest request) {
        return expiresAt(request).orElse(null);
    }

    /**
     * 대기 중이고 기한이 지났는지(꺼져 있으면 false).
     */
    public boolean isOverdue(WishRequest request, LocalDateTime now) {
        return expiresAt(request).map(expiresAt -> !now.isBefore(expiresAt)).orElse(false);
    }

    /**
     * 만료 작업이 만료해도 되는지: 대기 중이고 기한 + 유예(grace)가 지났는지(꺼져 있으면 false). 기한이 지났어도 유예 안이면 기한 전에
     * 만든 주문의 결제 완료를 기다린다.
     */
    public boolean isExpirable(WishRequest request, LocalDateTime now) {
        return expiresAt(request).map(expiresAt -> !now.isBefore(expiresAt.plus(properties.grace()))).orElse(false);
    }

    /**
     * 이 시각 이전(포함)에 보낸 조르기는 기한 + 유예가 지났다(만료 작업 대상 조회 기준).
     */
    public LocalDateTime sentBeforeExpirable(LocalDateTime now) {
        return now.minus(properties.ttl()).minus(properties.grace());
    }
}
