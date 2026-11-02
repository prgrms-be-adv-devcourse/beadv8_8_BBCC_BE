package com.bbcc.kidly.boundedcontext.review.in.web.dto;

import com.bbcc.kidly.boundedcontext.review.app.ReviewEligibilityQueryService.EligibleItem;
import com.bbcc.kidly.boundedcontext.review.app.ReviewEligibilityQueryService.EligiblePage;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 리뷰를 쓸 수 있는 주문상품 목록. ID는 문자열, 시각은 LocalDateTime(한국 시간).
 *
 * @param nextCursor 다음 쪽 커서(불투명 문자열). 마지막 쪽이면 null
 */
public record EligibleReviewsResponse(List<Item> items, String nextCursor) {

    public static EligibleReviewsResponse from(EligiblePage page) {
        return new EligibleReviewsResponse(page.items().stream().map(Item::from).toList(), page.nextCursor());
    }

    /**
     * @param productName 상품 정보를 아직 받지 못했으면 null
     * @param thumbnailUrl 상품 정보를 아직 받지 못했거나 썸네일이 없으면 null
     * @param size 사이즈 없는 상품이면 null
     */
    public record Item(
        String orderItemId,
        String orderId,
        String productId,
        String productName,
        String thumbnailUrl,
        String size,
        LocalDateTime confirmedAt) {

        static Item from(EligibleItem item) {
            return new Item(String.valueOf(item.orderItemId()),
                item.orderId() == null ? null : String.valueOf(item.orderId()),
                String.valueOf(item.productId()), item.productName(), item.thumbnailUrl(), item.size(),
                item.confirmedAt());
        }
    }
}
