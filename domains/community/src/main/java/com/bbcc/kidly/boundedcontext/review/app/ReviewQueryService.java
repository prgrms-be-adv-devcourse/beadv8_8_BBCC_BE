package com.bbcc.kidly.boundedcontext.review.app;

import com.bbcc.kidly.boundedcontext.review.app.port.ReviewStatsReader;
import com.bbcc.kidly.boundedcontext.review.app.port.Reviews;
import com.bbcc.kidly.boundedcontext.review.app.port.Reviews.ReviewListRow;
import com.bbcc.kidly.global.error.BusinessException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 리뷰 목록(상품별 · 내 리뷰)과 상품 리뷰 요약. 목록은 최신순 커서이고 한 쪽은 size건, 다음 쪽이 있는지는 1건 더 읽어 안다(쿼리 1번).
 * 판매 종료 상품의 리뷰도 그대로 보인다(표시 상태로 구분).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewQueryService {

    private final Reviews reviews;
    private final ReviewStatsReader reviewStatsReader;

    /**
     * @param productId 상품 ID(문자열). 없거나 양의 정수가 아니면 INVALID_PRODUCT_ID
     */
    public ReviewPage findByProduct(String productId, String cursor, String size) {
        Long id = parseProductId(productId);
        return page(cursor, size, limit -> reviews.findByProduct(id, limit),
            (after, limit) -> reviews.findByProductAfter(id, after.at(), after.id(), limit));
    }

    public ReviewPage findMine(Long memberId, String cursor, String size) {
        return page(cursor, size, limit -> reviews.findByMember(memberId, limit),
            (after, limit) -> reviews.findByMemberAfter(memberId, after.at(), after.id(), limit));
    }

    /**
     * 리뷰 수 · 평균 별점. 리뷰가 없으면 0 · null. 상품 ID는 파싱한 값으로 돌려준다(요청의 "+14" · "014"도 14).
     */
    public ProductSummary summary(String productId) {
        Long id = parseProductId(productId);
        ReviewSummary summary = reviewStatsReader.findById(id)
            .map(stats -> ReviewSummary.of(stats.getReviewCount(), stats.getRatingSum()))
            .orElseGet(ReviewSummary::none);
        return new ProductSummary(id, summary.reviewCount(), summary.averageRating());
    }

    private static ReviewPage page(String cursor, String size,
        Function<Limit, List<ReviewListRow>> first,
        BiFunction<PageRequestValues.Cursor, Limit, List<ReviewListRow>> after) {
        int pageSize = PageRequestValues.pageSize(size);
        Limit limit = Limit.of(pageSize + 1);
        List<ReviewListRow> rows = cursor == null
            ? first.apply(limit)
            : after.apply(PageRequestValues.Cursor.decode(cursor), limit);
        List<ReviewItem> items = rows.stream().limit(pageSize).map(ReviewItem::from).toList();
        boolean hasNext = rows.size() > pageSize;
        return new ReviewPage(items, hasNext
            ? new PageRequestValues.Cursor(items.getLast().createdAt(), items.getLast().reviewId()).encode()
            : null);
    }

    private static Long parseProductId(String productId) {
        try {
            long value = Long.parseLong(productId);
            if (value > 0) {
                return value;
            }
        } catch (NumberFormatException e) {
            // 아래에서 같은 오류로 응답한다(null 포함)
        }
        throw new BusinessException(ReviewErrorCode.INVALID_PRODUCT_ID);
    }

    /**
     * @param averageRating 리뷰가 없으면 null
     */
    public record ProductSummary(Long productId, long reviewCount, BigDecimal averageRating) {
    }

    /**
     * @param nextCursor 다음 쪽 커서. 마지막 쪽이면 null
     */
    public record ReviewPage(List<ReviewItem> items, String nextCursor) {
    }

    /**
     * @param productName 상품 복사본이 없으면 null
     * @param thumbnailUrl 상품 복사본이 없거나 썸네일이 없으면 null
     * @param displayStatus 표시 상태 이름(ON_SALE · SOLD_OUT · SALE_ENDED). 상품 복사본이 없으면 null
     * @param nickname 작성 시점 닉네임. 작성자가 탈퇴했으면 "탈퇴한 회원", 몰랐으면 null
     * @param authorWithdrawn 작성자가 탈퇴했는지
     */
    public record ReviewItem(
        Long reviewId,
        Long orderItemId,
        Long productId,
        String productName,
        String thumbnailUrl,
        String displayStatus,
        int rating,
        String content,
        String nickname,
        boolean authorWithdrawn,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

        static ReviewItem from(ReviewListRow row) {
            boolean withdrawn = row.getAuthorWithdrawnAt() != null;
            return new ReviewItem(row.getId(), row.getOrderItemId(), row.getProductId(), row.getProductName(),
                row.getThumbnailUrl(), row.getDisplayStatus() == null ? null : row.getDisplayStatus().name(),
                row.getRating().intValue(), row.getContent(),
                AuthorDisplay.nickname(row.getNickname(), withdrawn), withdrawn, row.getCreatedAt(),
                row.getUpdatedAt());
        }
    }
}
