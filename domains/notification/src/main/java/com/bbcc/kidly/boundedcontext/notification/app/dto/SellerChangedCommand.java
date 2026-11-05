package com.bbcc.kidly.boundedcontext.notification.app.dto;

import com.bbcc.kidly.shared.seller.domain.SellerStatus;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 판매자 승인 · 정지 메일을 가릴 판매자 변경.
 *
 * @param eventId 판매자 변경 이벤트 ID. 같은 이벤트로는 한 통만 만든다
 * @param sellerId 판매자 ID. 회원 ID와 같다
 * @param occurredAt 이벤트 발생 시각. 복사본보다 늦을 때만 반영한다
 */
public record SellerChangedCommand(UUID eventId, Long sellerId, String businessName, SellerStatus status,
    LocalDateTime occurredAt) {
}
