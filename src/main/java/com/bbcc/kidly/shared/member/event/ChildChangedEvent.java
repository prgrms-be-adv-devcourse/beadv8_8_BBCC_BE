package com.bbcc.kidly.shared.member.event;

import com.bbcc.kidly.shared.event.DomainEvent;
import com.bbcc.kidly.shared.member.domain.BodyType;
import com.bbcc.kidly.shared.member.domain.Gender;
import com.bbcc.kidly.shared.member.domain.PreferredFit;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 자녀가 등록 · 수정 · 삭제됐다. 추천 · 주문 도메인이 자녀 복사본을 갱신한다.
 * 바뀐 뒤의 전체 값을 담으므로 받는 쪽은 덮어쓰기만 한다. 저장된 값보다 {@code occurredAt}이 늦을 때만 반영한다.
 *
 * @param bodyType 입력하지 않았으면 null
 * @param preferredFit 입력하지 않았으면 null
 * @param deleted true면 자녀가 삭제됐다. 받는 쪽은 복사본을 지운다
 */
public record ChildChangedEvent(
    UUID eventId,
    Long childId,
    Long memberId,
    BigDecimal heightCm,
    BigDecimal weightKg,
    Gender gender,
    LocalDate birthDate,
    BodyType bodyType,
    PreferredFit preferredFit,
    boolean deleted,
    LocalDateTime occurredAt) implements DomainEvent {

    @Override
    public String aggregateId() {
        return String.valueOf(childId);
    }
}
