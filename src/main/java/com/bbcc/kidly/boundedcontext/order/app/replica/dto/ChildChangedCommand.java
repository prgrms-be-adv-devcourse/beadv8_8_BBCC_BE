package com.bbcc.kidly.boundedcontext.order.app.replica.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 자녀 변경을 복사본에 반영하는 입력. 이벤트에서 주문에 필요한 값만 옮긴다.
 *
 * @param name 자녀 이름. 이벤트에 아직 없어서 null
 * @param bodyType 체형 이름. 입력하지 않았으면 null
 */
public record ChildChangedCommand(
    Long childId,
    Long memberId,
    String name,
    BigDecimal heightCm,
    BigDecimal weightKg,
    String bodyType,
    boolean deleted,
    LocalDateTime occurredAt) {
}
