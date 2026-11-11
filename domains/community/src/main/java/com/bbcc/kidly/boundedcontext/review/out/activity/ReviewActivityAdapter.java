package com.bbcc.kidly.boundedcontext.review.out.activity;

import com.bbcc.kidly.boundedcontext.activity.app.port.in.ActivityRecorder;
import com.bbcc.kidly.boundedcontext.review.app.port.ReviewActivityLog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 리뷰 쪽 활동을 활동 컨텍스트에 남긴다(값만 옮겨 담는다).
 */
@Component
@RequiredArgsConstructor
public class ReviewActivityAdapter implements ReviewActivityLog {

    private final ActivityRecorder recorder;

    @Override
    public void eligibilityCreated(Long eligibilityId, Long productId, Long orderItemId) {
        recorder.record(new ActivityRecorder.EligibilityCreated(eligibilityId, productId, orderItemId));
    }

    @Override
    public void reviewCreated(Long reviewId, Long productId, Long orderItemId, int rating, String fit, int imageCount) {
        recorder.record(new ActivityRecorder.ReviewCreated(reviewId, productId, orderItemId, rating, fit, imageCount));
    }

    @Override
    public void reviewUpdated(Long reviewId, Long productId, int rating, String fit, int imageCount,
        boolean contentChanged, boolean imagesChanged) {
        recorder.record(new ActivityRecorder.ReviewUpdated(reviewId, productId, rating, fit, imageCount,
            contentChanged, imagesChanged));
    }

    @Override
    public void reviewDeleted(Long reviewId, Long productId, int rating, boolean wasPublic) {
        recorder.record(new ActivityRecorder.ReviewDeleted(reviewId, productId, rating, wasPublic));
    }

    @Override
    public void moderationDecided(Long reviewId, Long productId, boolean byAdmin, String status, String reason) {
        recorder.record(new ActivityRecorder.ModerationDecided(reviewId, productId, byAdmin, status, reason));
    }
}
