package com.bbcc.kidly.boundedcontext.order.app.replica.dto;

import java.time.LocalDateTime;

/**
 * 판매자 변경을 복사본에 반영하는 입력. 상태는 판매자 도메인 enum의 이름 문자열.
 */
public record SellerChangedCommand(
    Long sellerId,
    String businessName,
    String status,
    LocalDateTime occurredAt) {
}
