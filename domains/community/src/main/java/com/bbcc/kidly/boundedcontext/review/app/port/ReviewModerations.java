package com.bbcc.kidly.boundedcontext.review.app.port;

import com.bbcc.kidly.boundedcontext.review.domain.ModerationReason;
import com.bbcc.kidly.boundedcontext.review.domain.ModerationStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Limit;

/**
 * 검사용 리뷰 조회(검사 대기 목록 · 관리자 상태별 목록). 구현은 out/persistence(리뷰 저장소).
 */
public interface ReviewModerations {

    /**
     * 검사 대기(PENDING) 중 판단 못 한 횟수가 상한보다 적은 것을 id 순으로. 인덱스 (moderation_status, id)를 쓴다.
     */
    List<ModerationCandidateRow> findModerationCandidates(int maxAttempts, Limit limit);

    /**
     * 관리자 목록 첫 쪽: 그 상태의 리뷰를 최신순(id desc).
     */
    List<AdminReviewRow> findByModerationStatus(ModerationStatus status, Limit limit);

    /**
     * 관리자 목록 다음 쪽: id가 커서보다 작은 것.
     */
    List<AdminReviewRow> findByModerationStatusAfter(ModerationStatus status, Long id, Limit limit);

    /**
     * 재시도 상한에 닿은 검사 대기 리뷰(PENDING, 시도 횟수 ≥ 상한)의 시도 횟수를 0으로 한 문장에 되돌린다. 수정 시각은 바꾸지 않는다.
     *
     * @return 되돌린 리뷰 수
     */
    int resetExhaustedAttempts(int maxAttempts);

    interface ModerationCandidateRow {

        Long getId();

        long getContentVersion();

        int getModerationAttempts();

        String getContent();
    }

    interface AdminReviewRow {

        Long getId();

        Long getProductId();

        ModerationStatus getModerationStatus();

        ModerationReason getModerationReason();

        LocalDateTime getModeratedAt();

        int getModerationAttempts();

        String getContent();

        LocalDateTime getCreatedAt();
    }
}
