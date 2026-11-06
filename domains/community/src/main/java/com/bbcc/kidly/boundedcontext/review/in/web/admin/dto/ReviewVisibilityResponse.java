package com.bbcc.kidly.boundedcontext.review.in.web.admin.dto;

import com.bbcc.kidly.boundedcontext.review.app.ReviewModerationAdminService.VisibilityChanged;

/**
 * @param status 바뀐 검사 상태(CONFIRMED_HIDDEN · RESTORED)
 */
public record ReviewVisibilityResponse(String reviewId, String status) {

    public static ReviewVisibilityResponse from(VisibilityChanged changed) {
        return new ReviewVisibilityResponse(String.valueOf(changed.reviewId()), changed.status());
    }
}
