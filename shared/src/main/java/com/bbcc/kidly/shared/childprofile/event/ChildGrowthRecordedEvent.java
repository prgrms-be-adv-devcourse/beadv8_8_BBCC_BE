package com.bbcc.kidly.shared.childprofile.event;

import com.bbcc.kidly.shared.childprofile.domain.BodyType;
import com.bbcc.kidly.shared.event.DomainEvent;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 자녀의 성장 이력이 하나 쌓였다(등록, 키 · 체형 수정). 추천 도메인이 성장 속도를 계산한다.
 * {@code occurredAt}은 {@code data.recordedAt}과 같다. 다시 보낸 이벤트는 {@code eventId}만 새 값이므로
 * 받는 쪽은 {@code growthRecordId}로 중복을 거른다.
 */
public record ChildGrowthRecordedEvent(UUID eventId, LocalDateTime occurredAt, Data data) implements DomainEvent {

    /**
     * @param heightCm 키(cm). 소수 첫째 자리(예: 115.0). 값 비교는 equals가 아니라 compareTo로 한다
     * @param bodyType 입력하지 않았으면 null
     */
    public record Data(Long growthRecordId, Long childId, BigDecimal heightCm, BodyType bodyType,
                       LocalDateTime recordedAt) {
    }

    @Override
    public String aggregateId() {
        return String.valueOf(data.childId());
    }
}
