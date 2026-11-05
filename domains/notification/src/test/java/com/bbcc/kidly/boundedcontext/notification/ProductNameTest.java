package com.bbcc.kidly.boundedcontext.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.notification.domain.ProductName;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProductNameTest {

    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 11, 5, 9, 0);

    @Test
    @DisplayName("더 늦게 발생한 이벤트면 이름을 덮어쓴다")
    void applyNewer() {
        var productName = ProductName.create(1L, "곰돌이 티셔츠", OCCURRED_AT);

        productName.applyIfNewer("곰돌이 반팔 티셔츠", OCCURRED_AT.plusSeconds(1));

        assertThat(productName.getName()).isEqualTo("곰돌이 반팔 티셔츠");
        assertThat(productName.getSourceOccurredAt()).isEqualTo(OCCURRED_AT.plusSeconds(1));
    }

    @Test
    @DisplayName("더 일찍 발생한 옛 이벤트는 무시한다")
    void ignoreOlder() {
        var productName = ProductName.create(1L, "곰돌이 티셔츠", OCCURRED_AT);

        productName.applyIfNewer("옛 이름", OCCURRED_AT.minusSeconds(1));

        assertThat(productName.getName()).isEqualTo("곰돌이 티셔츠");
        assertThat(productName.getSourceOccurredAt()).isEqualTo(OCCURRED_AT);
    }

    @Test
    @DisplayName("발생 시각은 마이크로초로 잘라, 같은 메시지를 다시 받으면 늦은 이벤트로 보지 않는다")
    void sameMessageWithNanos() {
        var occurredAt = OCCURRED_AT.plusNanos(123_456_789);
        var productName = ProductName.create(1L, "곰돌이 티셔츠", occurredAt);

        productName.applyIfNewer("다른 이름", occurredAt);

        assertThat(productName.getName()).isEqualTo("곰돌이 티셔츠");
        assertThat(productName.getSourceOccurredAt()).isEqualTo(OCCURRED_AT.plusNanos(123_456_000));
    }
}
