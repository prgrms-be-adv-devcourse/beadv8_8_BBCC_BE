package com.bbcc.kidly.boundedcontext.review.app;

import com.bbcc.kidly.boundedcontext.review.app.port.ReviewEligibilities;
import com.bbcc.kidly.boundedcontext.review.app.port.ReviewEligibilities.EligibleRow;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내 리뷰 작성 자격 목록(구매확정 최신순, 커서). 한 쪽은 size건이고, 다음 쪽이 있는지는 1건 더 읽어 안다(쿼리 1번).
 * 커서는 마지막 항목의 (구매확정 시각, id)를 Base64URL로 감싼 불투명 문자열이다. 받는 쪽은 안을 해석하지 않고 그대로 돌려준다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewEligibilityQueryService {

    private final ReviewEligibilities reviewEligibilities;

    /**
     * @param cursor 직전 응답의 nextCursor. 없으면 첫 쪽
     * @param size 1~50 정수. 없으면 20
     */
    public EligiblePage findEligible(Long memberId, String cursor, String size) {
        int pageSize = PageRequestValues.pageSize(size);
        Limit limit = Limit.of(pageSize + 1);
        List<EligibleRow> rows = cursor == null
            ? reviewEligibilities.findEligible(memberId, limit)
            : findAfter(memberId, PageRequestValues.Cursor.decode(cursor), limit);
        boolean hasNext = rows.size() > pageSize;
        List<EligibleItem> items = rows.stream().limit(pageSize).map(EligibleItem::from).toList();
        return new EligiblePage(items, hasNext ? cursorOf(items.getLast()).encode() : null);
    }

    private List<EligibleRow> findAfter(Long memberId, PageRequestValues.Cursor cursor, Limit limit) {
        return reviewEligibilities.findEligibleAfter(memberId, cursor.at(), cursor.id(), limit);
    }

    private static PageRequestValues.Cursor cursorOf(EligibleItem last) {
        return new PageRequestValues.Cursor(last.confirmedAt(), last.id());
    }

    /**
     * @param nextCursor 다음 쪽 커서. 마지막 쪽이면 null
     */
    public record EligiblePage(List<EligibleItem> items, String nextCursor) {
    }

    /**
     * @param id 자격 ID(커서용, 응답에는 내보내지 않는다)
     * @param productName 상품 복사본이 없으면 null
     * @param thumbnailUrl 상품 복사본이 없거나 썸네일이 없으면 null
     */
    public record EligibleItem(
        Long id,
        Long orderItemId,
        Long orderId,
        Long productId,
        String productName,
        String thumbnailUrl,
        String size,
        LocalDateTime confirmedAt) {

        static EligibleItem from(EligibleRow row) {
            return new EligibleItem(row.getId(), row.getOrderItemId(), row.getOrderId(), row.getProductId(),
                row.getProductName(), row.getThumbnailUrl(), row.getSize(), row.getConfirmedAt());
        }
    }
}
