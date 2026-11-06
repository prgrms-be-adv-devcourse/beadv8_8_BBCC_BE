package com.bbcc.kidly.boundedcontext.review.in.web.admin;

import com.bbcc.kidly.boundedcontext.review.app.ReviewModerationAdminService;
import com.bbcc.kidly.boundedcontext.review.in.web.admin.dto.AdminReviewListResponse;
import com.bbcc.kidly.boundedcontext.review.in.web.admin.dto.ReviewVisibilityRequest;
import com.bbcc.kidly.boundedcontext.review.in.web.admin.dto.ReviewVisibilityResponse;
import com.bbcc.kidly.global.security.AuthAdmin;
import com.bbcc.kidly.global.security.LoginAdmin;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 리뷰 검사: 상태별 목록과 숨김 확정 · 다시 공개.
 */
@RestController
@RequestMapping("/api/v1/admin/community/reviews")
@RequiredArgsConstructor
public class AdminCommunityReviewController {

    private final ReviewModerationAdminService service;

    /**
     * 상태별 목록(최신순 커서). 오류: 400 INVALID_MODERATION_STATUS · INVALID_PAGE_SIZE · INVALID_CURSOR.
     *
     * @param status PENDING · PASSED · HIDDEN · CONFIRMED_HIDDEN · RESTORED(필수)
     */
    @GetMapping
    public AdminReviewListResponse list(@LoginAdmin AuthAdmin admin,
        @RequestParam(required = false) String status, @RequestParam(required = false) String cursor,
        @RequestParam(required = false) String size) {
        return AdminReviewListResponse.from(service.list(status, cursor, size));
    }

    /**
     * 200 + 바뀐 상태(HIDDEN → CONFIRMED_HIDDEN, VISIBLE → RESTORED). 같은 값을 다시 보내도 200.
     * 오류: 400 INVALID_VISIBILITY, 404 REVIEW_NOT_FOUND.
     */
    @PatchMapping("/{reviewId}/visibility")
    public ReviewVisibilityResponse changeVisibility(@LoginAdmin AuthAdmin admin, @PathVariable Long reviewId,
        @RequestBody ReviewVisibilityRequest request) {
        return ReviewVisibilityResponse.from(service.changeVisibility(admin.id(), reviewId, request.value()));
    }
}
