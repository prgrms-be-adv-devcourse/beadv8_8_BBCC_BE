package com.bbcc.kidly.boundedcontext.delivery.out.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Delivery Tracker 연결 설정. 기본값은 order.yml 한 곳에만 둔다.
 * 인증값이 비었거나 헤더에 넣을 수 없는 값이면 비활성(기동은 하고 조회만 하지 않는다).
 *
 * @param baseUrl API 주소
 * @param clientId 환경변수 DELIVERY_TRACKER_CLIENT_ID
 * @param clientSecret 환경변수 DELIVERY_TRACKER_CLIENT_SECRET
 * @param connectTimeout 연결 제한 시간
 * @param readTimeout 응답 제한 시간
 * @param minCallInterval 호출 사이 최소 간격(호출 한도 초당 10회)
 */
@Validated
@ConfigurationProperties("kidly.order.delivery-tracker")
public record DeliveryTrackerProperties(
    @NotBlank String baseUrl,
    String clientId,
    String clientSecret,
    @NotNull Duration connectTimeout,
    @NotNull Duration readTimeout,
    @NotNull Duration minCallInterval) {

    public boolean enabled() {
        return isHeaderSafe(clientId) && isHeaderSafe(clientSecret);
    }

    // 헤더에 못 넣는 값이면 JDK 예외 메시지에 헤더 값(인증값)이 그대로 찍히므로 미리 막는다(10/15 스파이크에서 확인)
    private static boolean isHeaderSafe(String value) {
        return value != null && !value.isBlank() && value.chars().allMatch(c -> c > 0x20 && c < 0x7F);
    }

    // record 기본 toString은 인증값을 그대로 찍으므로 가린다
    @Override
    public String toString() {
        return "DeliveryTrackerProperties[baseUrl=" + baseUrl + ", enabled=" + enabled() + "]";
    }
}
