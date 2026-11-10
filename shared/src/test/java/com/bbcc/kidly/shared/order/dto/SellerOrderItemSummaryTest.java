package com.bbcc.kidly.shared.order.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 판매자 주문상품 요약 계약(#1120): 칸 이름 · 순서가 내부 API 응답과 같다.
 */
class SellerOrderItemSummaryTest {

    @Test
    @DisplayName("칸은 sellerId · openItemCount 두 개(판매자 상태 같은 회원 개념 없음)")
    void fields() {
        assertThat(Arrays.stream(SellerOrderItemSummary.class.getRecordComponents()).map(RecordComponent::getName))
            .containsExactly("sellerId", "openItemCount");
        assertThat(new SellerOrderItemSummary(12L, 3L).openItemCount()).isEqualTo(3L);
    }
}
