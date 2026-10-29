package com.bbcc.kidly.boundedcontext.product.app.outbox;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 상품 아웃박스 릴레이 설정. batchSize는 한 번에 보낼 최대 건수이고, retention은 보낸 메시지를 남겨 두는 기간이다.
 */
@ConfigurationProperties("kidly.product.outbox")
public record ProductOutboxProperties(int batchSize, Duration retention) {
}
