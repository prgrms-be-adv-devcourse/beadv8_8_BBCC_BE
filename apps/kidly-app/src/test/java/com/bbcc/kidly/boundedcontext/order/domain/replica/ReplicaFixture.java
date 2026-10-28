package com.bbcc.kidly.boundedcontext.order.domain.replica;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 복사본 테스트 데이터. 상품 1(판매자 2) · 자녀 1(보호자 1). 도메인 테스트와 기능 테스트가 함께 쓴다.
 */
public final class ReplicaFixture {

    public static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 10, 13, 9, 0);
    public static final Long SELLER_ID = 2L;
    public static final Long BUYER_ID = 1L;

    private ReplicaFixture() {
    }

    public static ReplicaProduct.Values product(Long optionId, long unitPrice, String status, boolean soldOut,
        LocalDateTime occurredAt) {
        return new ReplicaProduct.Values(optionId, 1L, SELLER_ID, "상품 A", "네이비 / 110", "110", unitPrice, 1L, status,
            "ON_SALE", soldOut, occurredAt);
    }

    /**
     * 상품 판매 상태 · 옵션 판매 상태 · 품절을 정한 옵션(단가 25,000원).
     */
    public static ReplicaProduct.Values option(Long optionId, String productStatus,
        String optionSaleStatus, boolean soldOut, LocalDateTime occurredAt) {
        return new ReplicaProduct.Values(optionId, 1L, SELLER_ID, "상품 A", "네이비 / 110", "110", 25_000L, 1L,
            productStatus, optionSaleStatus, soldOut, occurredAt);
    }

    public static ReplicaProduct.Values onSaleProduct(long unitPrice, LocalDateTime occurredAt) {
        return product(1L, unitPrice, "ON_SALE", false, occurredAt);
    }

    public static ReplicaChild.Values child(String height, boolean deleted, LocalDateTime occurredAt) {
        return new ReplicaChild.Values(1L, BUYER_ID, null, new BigDecimal(height), new BigDecimal("20.0"), "NORMAL",
            deleted, occurredAt);
    }
}
