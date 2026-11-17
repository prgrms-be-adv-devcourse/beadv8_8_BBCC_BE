package com.bbcc.kidly.shared.community.event;

import com.bbcc.kidly.shared.event.DomainEvent;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 회원 또는 자녀 계정이 상품을 새로 찜했다(이미 찜한 상품을 다시 찜한 요청은 보내지 않는다). {@code aggregateId}는 찜 ID,
 * 같은 가족의 찜 · 찜 해제 순서를 지키도록 {@code correlationId}(Kafka 키)는 {@code memberId}(보호자 회원 ID)다.
 * {@code eventType}은 {@code favorite.added}.
 */
public record FavoriteAddedEvent(UUID eventId, LocalDateTime occurredAt, Data data) implements DomainEvent {

    /**
     * @param memberId 회원 본인 찜이면 그 회원 ID, 자녀 계정 찜이면 보호자 회원 ID
     * @param ownerType 찜 소유자 종류. 값은 {@link FavoriteOwnerTypes#MEMBER} · {@link FavoriteOwnerTypes#CHILD}뿐이고,
     *     칸이 없으면(옛 메시지) MEMBER다. 받는 쪽은 이 값으로 자녀 계정 찜을 구분한다
     *     (자녀 계정 찜의 memberId는 보호자 회원 ID라 memberId만으로는 구분할 수 없다)
     * @param ownerId 찜 소유자 ID(MEMBER는 회원 ID, CHILD는 자녀 계정 ID). 없으면(옛 메시지) memberId
     */
    public record Data(
        Long favoriteId,
        Long memberId,
        Long productId,
        String ownerType,
        Long ownerId) {

        /**
         * 소유자 칸 없이 만든다(옛 형식).
         */
        public Data(Long favoriteId, Long memberId, Long productId) {
            this(favoriteId, memberId, productId, null, null);
        }
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
