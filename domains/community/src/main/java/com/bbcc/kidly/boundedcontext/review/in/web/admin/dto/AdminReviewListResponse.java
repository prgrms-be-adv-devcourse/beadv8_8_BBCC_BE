package com.bbcc.kidly.boundedcontext.review.in.web.admin.dto;

import com.bbcc.kidly.boundedcontext.review.app.ReviewModerationAdminService.AdminReviewItem;
import com.bbcc.kidly.boundedcontext.review.app.ReviewModerationAdminService.AdminReviewPage;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 관리자 리뷰 검사 목록. ID는 문자열, 시각은 LocalDateTime(한국 시간).
 *
 * @param nextCursor 다음 쪽 커서. 마지막 쪽이면 null
 */
public record AdminReviewListResponse(List<Item> items, String nextCursor) {

    public static AdminReviewListResponse from(AdminReviewPage page) {
        return new AdminReviewListResponse(page.items().stream().map(Item::from).toList(), page.nextCursor());
    }

    /**
     * @param status 검사 상태(PENDING · PASSED · HIDDEN · CONFIRMED_HIDDEN · RESTORED)
     * @param reason 걸린 이유 분류(PROFANITY · HATE · SEXUAL · VIOLENCE · ILLEGAL · PRIVACY · OTHER). 없으면 null
     * @param moderatedAt 마지막 검사 결과 · 관리자 조치 시각. 없으면 null
     * @param attempts 판단 못 한 검사 횟수
     * @param attemptsExhausted 재시도 상한에 닿아 더 검사하지 않는 검사 대기 리뷰인지(공개 그대로, 관리자가 판단한다)
     * @param imageUrls 사진 공개 주소(순서대로)
     */
    public record Item(String reviewId, String productId, String status, String reason, LocalDateTime moderatedAt,
                       int attempts, boolean attemptsExhausted, String content, List<String> imageUrls,
                       LocalDateTime createdAt) {

        static Item from(AdminReviewItem item) {
            return new Item(String.valueOf(item.reviewId()), String.valueOf(item.productId()), item.status(),
                item.reason(), item.moderatedAt(), item.attempts(), item.attemptsExhausted(), item.content(),
                item.imageUrls(), item.createdAt());
        }
    }
}
