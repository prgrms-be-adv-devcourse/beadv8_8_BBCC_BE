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
 * 삭제({@code deleted = true})여도 삭제 직전 값을 그대로 담는다. {@code bodyType} · {@code preferredFit} 외에는 null이 아니다.
 */
public record ChildChangedEvent(UUID eventId, LocalDateTime occurredAt, Data data) implements DomainEvent {

    /**
     * @param heightCm 키(cm). 소수 첫째 자리까지(예: 115.0). 값 비교는 equals가 아니라 compareTo로 한다
     * @param weightKg 몸무게(kg). 소수 첫째 자리까지(예: 20.0). 값 비교는 equals가 아니라 compareTo로 한다
     * @param bodyType 입력하지 않았으면 null
     * @param preferredFit 입력하지 않았으면 null
     * @param deleted true면 자녀가 삭제됐다. 받는 쪽은 복사본을 지운다
     */
    public record Data(
        Long childId,
        Long memberId,
        BigDecimal heightCm,
        BigDecimal weightKg,
        Gender gender,
        LocalDate birthDate,
        BodyType bodyType,
        PreferredFit preferredFit,
        boolean deleted) {
    }

    @Override
    public String aggregateId() {
        return String.valueOf(data.childId());
    }
}
