package com.bbcc.kidly.boundedcontext.review.app;

import com.bbcc.kidly.boundedcontext.review.app.ReviewModerationService.Candidate;
import com.bbcc.kidly.boundedcontext.review.app.port.ContentModerator.FailureCause;
import com.bbcc.kidly.boundedcontext.review.app.port.ContentModerator.Result;
import com.bbcc.kidly.boundedcontext.review.app.port.ReviewActivityLog;
import com.bbcc.kidly.boundedcontext.review.app.port.Reviews;
import com.bbcc.kidly.boundedcontext.review.domain.ModerationStatus;
import com.bbcc.kidly.boundedcontext.review.domain.Review;
import com.bbcc.kidly.boundedcontext.review.domain.ReviewTimes;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 검사 결과 한 건을 한 트랜잭션으로 반영한다. 리뷰를 행 잠금으로 다시 읽어, 검사기에 넘길 때의 버전 · 시도 횟수가 그대로이고 아직
 * 대기일 때만 반영한다(검사 중 글 수정 · 관리자 조치 · 다른 처리기의 반영이 있었으면 버린다). 숨기면 같은 트랜잭션에서 집계를 뺀다.
 * 판단 못 함은 시도 횟수 +1(장애 · 시간 초과), 한도 초과(429)는 그대로 둔다.
 */
@Service
@RequiredArgsConstructor
public class ReviewModerationRecorder {

    private final Reviews reviews;
    private final ReviewStatsShare statsShare;
    private final ReviewActivityLog activityLog;

    @Transactional
    public Recorded record(Candidate candidate, Result result) {
        Review review = reviews.findForUpdate(candidate.reviewId()).orElse(null);
        if (review == null || !review.isAwaitingModeration(candidate.contentVersion(), candidate.attempts())) {
            return Recorded.DISCARDED;
        }
        return switch (result.verdict()) {
            case PASSED -> {
                review.pass(ReviewTimes.now());
                activityLog.moderationDecided(review.getId(), review.getProductId(), review.getOrderItemId(),
                    review.getRating(), fitName(review), false, ModerationStatus.PASSED.name(), null,
                    review.isPublic());
                yield Recorded.PASSED;
            }
            case FLAGGED -> {
                review.hideByModeration(result.reason(), ReviewTimes.now());
                statsShare.remove(review);
                activityLog.moderationDecided(review.getId(), review.getProductId(), review.getOrderItemId(),
                    review.getRating(), fitName(review), false, ModerationStatus.HIDDEN.name(),
                    result.reason() == null ? null : result.reason().name(), review.isPublic());
                yield Recorded.HIDDEN;
            }
            case UNDECIDED -> {
                // 한도 초과는 검사기 장애가 아니라 시도 횟수를 올리지 않는다(일 한도가 바닥나도 검사 없이 상한에 닿아 빠지지 않게)
                if (result.cause() != FailureCause.RATE_LIMITED) {
                    review.recordUndecided();
                }
                yield Recorded.UNDECIDED;
            }
        };
    }

    private static String fitName(Review review) {
        return review.getFit() == null ? null : review.getFit().name();
    }

    public enum Recorded {
        PASSED,
        HIDDEN,
        UNDECIDED,
        // 그 사이 바뀌어 반영하지 않음
        DISCARDED
    }
}
