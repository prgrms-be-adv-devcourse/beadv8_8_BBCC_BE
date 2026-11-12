package com.bbcc.kidly.shared.community.event;

import com.bbcc.kidly.shared.event.DomainEvent;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 찜이 지워졌다(회원이 해제했거나 회원 탈퇴로 지웠다. 없는 찜 해제는 보내지 않는다). {@code aggregateId}는 찜 ID,
 * {@code correlationId}(Kafka 키)는 회원 ID다. {@code eventType}은 {@code favorite.removed}.
 */
public record FavoriteRemovedEvent(UUID eventId, LocalDateTime occurredAt, Data data) implements DomainEvent {

    /**
     * @param cause 지운 까닭
     */
    public record Data(
        Long favoriteId,
        Long memberId,
        Long productId,
        FavoriteRemovalCause cause) {
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
