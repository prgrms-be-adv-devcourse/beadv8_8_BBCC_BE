package com.bbcc.kidly.shared.community.event;

import com.bbcc.kidly.shared.event.DomainEvent;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 회원이 상품을 새로 찜했다(이미 찜한 상품을 다시 찜한 요청은 보내지 않는다). {@code aggregateId}는 찜 ID,
 * 같은 회원의 찜 · 찜 해제 순서를 지키도록 {@code correlationId}(Kafka 키)는 회원 ID다. {@code eventType}은 {@code favorite.added}.
 */
public record FavoriteAddedEvent(UUID eventId, LocalDateTime occurredAt, Data data) implements DomainEvent {

    public record Data(
        Long favoriteId,
        Long memberId,
        Long productId) {
    }

    @Override
    public String aggregateId() {
        return String.valueOf(data.favoriteId());
    }

    @Override
    public String correlationId() {
        return String.valueOf(data.memberId());
    }
}
