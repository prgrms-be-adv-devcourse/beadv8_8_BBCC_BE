package com.bbcc.kidly.boundedcontext.review.app;

import com.bbcc.kidly.boundedcontext.review.app.port.ReplicaProductStats;
import com.bbcc.kidly.boundedcontext.review.app.port.ReplicaProductStats.DisplayStatusCountRow;
import com.bbcc.kidly.boundedcontext.review.domain.DisplayStatus;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 상품 복사본 상태(운영 조회). 복사본 수 · 표시 상태별 수 · 마지막 반영 시각으로 빠진 상품 · 반영 지연을 본다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReplicaHealthService {

    private final ReplicaProductStats stats;

    public ReplicaHealth health() {
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (DisplayStatus status : DisplayStatus.values()) {
            byStatus.put(status.name(), 0L);
        }
        for (DisplayStatusCountRow row : stats.countByDisplayStatus()) {
            byStatus.put(row.getStatus().name(), row.getTotal());
        }
        return new ReplicaHealth(stats.count(), byStatus, stats.findLastUpdatedAt(),
            stats.findLastSourceOccurredAt());
    }

    /**
     * @param byDisplayStatus 표시 상태 이름 → 수(모든 상태, 없으면 0)
     * @param lastUpdatedAt 마지막 반영 시각. 복사본이 없으면 null
     * @param lastSourceOccurredAt 반영한 원본 이벤트 중 가장 늦은 발생 시각. 복사본이 없으면 null
     */
    public record ReplicaHealth(long total, Map<String, Long> byDisplayStatus, LocalDateTime lastUpdatedAt,
                                LocalDateTime lastSourceOccurredAt) {

        // 표시 상태 순서(ON_SALE · SOLD_OUT · SALE_ENDED)를 지킨다
        public ReplicaHealth {
            byDisplayStatus = Collections.unmodifiableMap(new LinkedHashMap<>(byDisplayStatus));
        }
    }
}
