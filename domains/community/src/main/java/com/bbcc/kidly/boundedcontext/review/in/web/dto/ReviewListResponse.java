package com.bbcc.kidly.boundedcontext.review.in.web.dto;

import com.bbcc.kidly.boundedcontext.review.app.ReviewQueryService.ReviewItem;
import com.bbcc.kidly.boundedcontext.review.app.ReviewQueryService.ReviewPage;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 리뷰 목록(상품별 · 내 리뷰 같은 모양). ID는 문자열, 시각은 LocalDateTime(한국 시간).
 *
 * @param nextCursor 다음 쪽 커서(불투명 문자열). 마지막 쪽이면 null
 */
public record ReviewListResponse(List<Item> items, String nextCursor) {

    public static ReviewListResponse from(ReviewPage page) {
        return new ReviewListResponse(page.items().stream().map(Item::from).toList(), page.nextCursor());
    }

    /**
     * @param productName 상품 정보를 아직 받지 못했으면 null
     * @param thumbnailUrl 상품 정보를 아직 받지 못했거나 썸네일이 없으면 null
     * @param displayStatus ON_SALE · SOLD_OUT · SALE_ENDED. 상품 정보를 아직 받지 못했으면 null
     * @param nickname 작성 시점 닉네임. 작성자가 탈퇴했으면 "탈퇴한 회원", 몰랐으면 null
     * @param authorWithdrawn 작성자가 탈퇴했는지
     * @param fit 사이즈 핏(SMALL · TRUE_TO_SIZE · LARGE). 남기지 않았으면 null
     * @param purchasedSize 구매한 사이즈. 없는 상품이면 null
     * @param imageUrls 사진 공개 주소(순서대로). 없으면 빈 목록
     * @param visibility PUBLIC(공개) · UNDER_REVIEW(검토 중, 내 리뷰에만 보인다). 상품 목록은 늘 PUBLIC
     */
    public record Item(
        String reviewId,
        String orderItemId,
        String productId,
        String productName,
        String thumbnailUrl,
        String displayStatus,
        int rating,
        String content,
        String nickname,
        boolean authorWithdrawn,
        String fit,
        String purchasedSize,
        List<String> imageUrls,
        String visibility,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

        static Item from(ReviewItem item) {
            return new Item(String.valueOf(item.reviewId()), String.valueOf(item.orderItemId()),
                String.valueOf(item.productId()), item.productName(), item.thumbnailUrl(),
                item.displayStatus(), item.rating(), item.content(),
                item.nickname(), item.authorWithdrawn(), item.fit(), item.purchasedSize(), item.imageUrls(),
                item.visibility(), item.createdAt(), item.updatedAt());
        }
    }
}
