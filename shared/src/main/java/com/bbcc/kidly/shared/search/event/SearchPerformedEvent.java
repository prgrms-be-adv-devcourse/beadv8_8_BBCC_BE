package com.bbcc.kidly.shared.search.event;

import com.bbcc.kidly.shared.event.DomainEvent;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 로그인 회원이 검색어로 상품 목록을 찾았다(#611). 추천이 회원의 관심사로 쓴다.
 * 검색어가 있는 첫 페이지 조회만 보낸다. 비로그인 · 검색어 없는 둘러보기 · 다음 페이지는 보내지 않는다.
 * 검색 시각은 {@code occurredAt}이다. 결과 목록(어떤 상품이 나왔는지)은 담지 않는다.
 */
public record SearchPerformedEvent(UUID eventId, LocalDateTime occurredAt, Data data) implements DomainEvent {

    /**
     * @param memberId 검색한 회원 ID
     * @param keyword 검색어. 앞뒤 공백을 뺀 값이다
     * @param categoryId 고른 카테고리 ID. 고르지 않았으면 null
     * @param attributeValueIds 고른 항목 값 ID를 항목 code별로 묶은 것(예: COLOR → [3, 5]). 없으면 빈 맵
     * @param resultCount 조건에 맞는 전체 상품 수. 0건도 보낸다
     */
    public record Data(Long memberId, String keyword, Long categoryId, Map<String, List<Long>> attributeValueIds,
                       long resultCount) {
    }

    @Override
    public String aggregateId() {
        return String.valueOf(data.memberId());
    }
}
