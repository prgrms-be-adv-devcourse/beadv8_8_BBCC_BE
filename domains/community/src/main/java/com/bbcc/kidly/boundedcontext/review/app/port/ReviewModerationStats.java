package com.bbcc.kidly.boundedcontext.review.app.port;

import com.bbcc.kidly.boundedcontext.review.domain.ModerationReason;
import com.bbcc.kidly.boundedcontext.review.domain.ModerationStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Limit;

/**
 * 검사 현황 읽기(운영 조회). 구현은 out/persistence(리뷰 저장소). 읽기 쿼리만 둔다.
 */
public interface ReviewModerationStats {

    /**
     * 검사 상태별 리뷰 수(리뷰가 없는 상태는 빠진다).
     */
    List<StatusCountRow> countByModerationStatus();

    /**
     * 재시도 상한에 닿아 대기 목록에서 빠진 검사 대기 리뷰 수.
     */
    long countExhaustedPending(int maxAttempts);

    /**
     * 대기 목록(상한 전)에서 가장 오래된 리뷰의 작성 시각. 없으면 null.
     */
    LocalDateTime findOldestPendingCreatedAt(int maxAttempts);

    /**
     * 검사가 숨긴(HIDDEN) 리뷰를 최근 숨긴 순으로. 본문 · 작성자는 읽지 않는다.
     */
    List<HiddenRow> findRecentlyHidden(Limit limit);

    interface StatusCountRow {

        ModerationStatus getStatus();

        long getTotal();
    }

    interface HiddenRow {

        Long getId();

        ModerationReason getReason();

        LocalDateTime getModeratedAt();
    }
}
