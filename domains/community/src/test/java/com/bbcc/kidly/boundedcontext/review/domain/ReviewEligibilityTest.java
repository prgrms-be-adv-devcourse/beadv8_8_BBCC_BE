package com.bbcc.kidly.boundedcontext.review.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReviewEligibilityTest {

    @Test
    @DisplayName("구매확정 값으로 작성 자격을 만든다: 주문상품 · 주문 · 회원 · 상품 · 옵션 · 사이즈 · 구매확정 시각")
    void create() {
        LocalDateTime confirmedAt = LocalDateTime.now().minusMinutes(5);

        ReviewEligibility eligibility = ReviewEligibility.create(
            new ReviewEligibility.Values(11L, 12L, 13L, 14L, 15L, "110", confirmedAt));

        assertThat(eligibility.getOrderItemId()).isEqualTo(11L);
        assertThat(eligibility.getOrderId()).isEqualTo(12L);
        assertThat(eligibility.getMemberId()).isEqualTo(13L);
        assertThat(eligibility.getProductId()).isEqualTo(14L);
        assertThat(eligibility.getOptionId()).isEqualTo(15L);
        assertThat(eligibility.getSize()).isEqualTo("110");
        assertThat(eligibility.getConfirmedAt()).isEqualTo(confirmedAt);
    }
}
