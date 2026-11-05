package com.bbcc.kidly.shared.seller.event;

import com.bbcc.kidly.shared.event.DomainEvent;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 판매자 신청이 거절됐다. 알림 도메인이 신청자에게 거절 사유를 안내한다.
 */
public record SellerApplicationRejectedEvent(UUID eventId, LocalDateTime occurredAt, Data data) implements DomainEvent {

    public record Data(Long applicationId, Long memberId, String reason, LocalDateTime rejectedAt) {
    }

    @Override
    public String aggregateId() {
        return String.valueOf(data.applicationId());
    }
}
