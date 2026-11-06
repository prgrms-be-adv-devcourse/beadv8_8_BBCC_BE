package com.bbcc.kidly.shared.search.event;

import com.bbcc.kidly.shared.event.DomainEvent;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 로그인 회원이 검색어로 찾은 상품 목록에서 상품을 눌렀다(#1004). 검색({@link SearchPerformedEvent})보다 강한 관심 신호다.
 * 누른 시각은 {@code occurredAt}이다. 키는 {@link SearchPerformedEvent}와 같은 회원 ID라 한 회원의 검색과 클릭이 순서대로 들어간다.
 */
public record SearchResultClickedEvent(UUID eventId, LocalDateTime occurredAt, Data data) implements DomainEvent {

    /**
     * @param memberId 누른 회원 ID
     * @param keyword 그 목록을 찾은 검색어. 앞뒤 공백을 뺀 값이다
     * @param productId 누른 상품 ID
     * @param rank 보여 준 목록에서 누른 상품의 위치. 첫 페이지 첫 상품이 1이다
     * @param sort 그 목록의 정렬(NEWEST · PRICE_ASC · PRICE_DESC · RELEVANCE). 순위가 정렬에 따라 달라서 함께 담는다
     */
    public record Data(Long memberId, String keyword, Long productId, int rank, String sort) {
    }

    @Override
    public String aggregateId() {
        return String.valueOf(data.memberId());
    }
}
