package com.bbcc.kidly.boundedcontext.review.app.port;

import com.bbcc.kidly.boundedcontext.review.domain.DisplayStatus;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 상품 복사본 상태 읽기(운영 조회: 빠진 상품 · 반영 지연 진단). 구현은 out/persistence(복사본 저장소). 읽기 쿼리만 둔다.
 */
public interface ReplicaProductStats {

    long count();

    /**
     * 표시 상태별 복사본 수(없는 상태는 빠진다).
     */
    List<DisplayStatusCountRow> countByDisplayStatus();

    /**
     * 마지막으로 반영(저장 · 덮어쓰기)된 시각. 복사본이 없으면 null.
     */
    LocalDateTime findLastUpdatedAt();

    /**
     * 반영한 원본 이벤트 중 가장 늦게 발생한 시각. 복사본이 없으면 null.
     */
    LocalDateTime findLastSourceOccurredAt();

    interface DisplayStatusCountRow {

        DisplayStatus getStatus();

        long getTotal();
    }
}
