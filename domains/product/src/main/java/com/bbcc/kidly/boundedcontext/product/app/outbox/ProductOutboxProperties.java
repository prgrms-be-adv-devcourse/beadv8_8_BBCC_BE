package com.bbcc.kidly.boundedcontext.product.app.outbox;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 상품 아웃박스 릴레이와 정리에 쓰는 설정이다. 값이 없거나 맞지 않으면 기동에 실패한다.
 * 값이 빠진 채 기동하면 릴레이나 정리가 실행할 때마다 실패해 이벤트 전송이 원인을 알기 어려운 채로 멈추기 때문이다.
 *
 * @param batchSize 한 번 실행에서 보내는 최대 건수. 1 이상
 * @param retention 보낸 메시지를 남겨 두는 기간. 지나면 정리가 지운다
 */
@Validated
@ConfigurationProperties("kidly.product.outbox")
public record ProductOutboxProperties(@Positive int batchSize, @NotNull Duration retention) {
}
