package com.bbcc.kidly.shared.community.event;

import com.bbcc.kidly.shared.event.DomainEvent;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 리뷰가 바뀌었다(작성 · 실제로 바뀐 수정 · 삭제 · 검사 결과 · 관리자 숨김 확정 · 다시 공개). 받는 쪽은 data의 지금 값으로 덮어쓴다.
 * {@code aggregateId} · {@code correlationId}(Kafka 키)는 리뷰 ID다. 클래스 이름 규칙으로 {@code eventType}은 {@code review.changed}.
 * 닉네임 · 본문 · 사진 주소는 담지 않는다.
 */
public record ReviewChangedEvent(UUID eventId, LocalDateTime occurredAt, Data data) implements DomainEvent {

    /**
     * @param rating 별점(1~5)
     * @param fit 사이즈 핏 SMALL · TRUE_TO_SIZE · LARGE(남기지 않았으면 null)
     * @param visible 지금 공개(목록 · 상품 요약에 들어감)인지. 삭제 · 검사 숨김 · 관리자 숨김 확정이면 false
     */
    public record Data(
        Long reviewId,
        Long orderItemId,
        Long productId,
        Integer rating,
        String fit,
        Boolean visible) {
    }

    @Override
    public String aggregateId() {
        return String.valueOf(data.reviewId());
    }
}
