package com.bbcc.kidly.boundedcontext.review.app;

import com.bbcc.kidly.boundedcontext.review.app.port.ReviewEligibilities;
import com.bbcc.kidly.boundedcontext.review.app.port.ReviewStatsCounter;
import com.bbcc.kidly.boundedcontext.review.app.port.Reviews;
import com.bbcc.kidly.boundedcontext.review.domain.Review;
import com.bbcc.kidly.boundedcontext.review.domain.ReviewEligibility;
import com.bbcc.kidly.boundedcontext.review.domain.ReviewRule;
import com.bbcc.kidly.boundedcontext.review.domain.ReviewRuleException;
import com.bbcc.kidly.boundedcontext.review.domain.ReviewTimes;
import com.bbcc.kidly.global.error.BusinessException;
import java.time.LocalDateTime;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 리뷰 작성 · 수정 · 삭제와 상품별 집계 증감.
 * <ul>
 *   <li>작성: 입력 확인 → 내 자격 확인(없으면 403) → 이미 쓴 리뷰 확인(있으면 409) → 저장과 집계 +1 · +별점을 한 트랜잭션에서.
 *       확인 뒤 같은 주문상품이 먼저 저장되면(동시 작성) 주문상품 유일 제약에 막히고, 그 위반은 409로 바꾼다(집계도 되돌려진다)</li>
 *   <li>수정 · 삭제: 리뷰를 행 잠금으로 읽고 작성자를 확인한 뒤, 수정은 별점 차이만큼 · 삭제는 −1 · −별점으로 집계를 바꾼다</li>
 * </ul>
 * 로그 · 예외 메시지에 본문 · 닉네임을 넣지 않는다.
 */
@Service
@RequiredArgsConstructor
public class ReviewService {

    // 주문상품당 리뷰 1개를 지키는 유일 제약(마이그레이션 V202611021140)
    static final String ORDER_ITEM_UNIQUE = "uk_review_order_item_id";

    private final Reviews reviews;
    private final ReviewStatsCounter reviewStatsCounter;
    private final ReviewEligibilities reviewEligibilities;
    private final ReviewSeparateTransaction separateTransaction;

    /**
     * @param nickname 작성 시점 닉네임(로그인 정보). 없으면 null로 저장한다
     * @param orderItemId 리뷰 작성 가능 목록의 orderItemId(문자열)
     */
    public ReviewView write(Long memberId, String nickname, String orderItemId, Integer rating, String content) {
        Long itemId = parseOrderItemId(orderItemId);
        translateRule(() -> {
            Review.checkInput(rating, content);
            return null;
        });
        ReviewEligibility eligibility = reviewEligibilities.findByOrderItemIdAndMemberId(itemId, memberId)
            .orElseThrow(() -> new BusinessException(ReviewErrorCode.REVIEW_NOT_ELIGIBLE));
        if (reviews.existsByOrderItemId(itemId)) {
            throw new BusinessException(ReviewErrorCode.REVIEW_ALREADY_WRITTEN);
        }
        Review review = Review.create(new Review.Values(itemId, eligibility.getProductId(), memberId, nickname, rating,
            content));
        try {
            return separateTransaction.call(() -> {
                Review saved = reviews.saveAndFlush(review);
                reviewStatsCounter.addReview(saved.getProductId(), saved.getRating(), ReviewTimes.now());
                return ReviewView.from(saved);
            });
        } catch (DataIntegrityViolationException e) {
            if (UniqueViolations.isViolationOf(e, ORDER_ITEM_UNIQUE)) {
                throw new BusinessException(ReviewErrorCode.REVIEW_ALREADY_WRITTEN);
            }
            throw e;
        }
    }

    /**
     * 별점 · 본문을 둘 다 고친다.
     */
    @Transactional
    public ReviewView update(Long memberId, Long reviewId, Integer rating, String content) {
        return update(memberId, reviewId, new ReviewChange(true, rating, true, content));
    }

    /**
     * 보낸 값만 고친다. 별점을 보내지 않으면 집계도 바뀌지 않는다.
     * 오류: 400 NO_FIELDS_TO_UPDATE · INVALID_RATING · INVALID_CONTENT, 403 REVIEW_FORBIDDEN, 404 REVIEW_NOT_FOUND.
     */
    @Transactional
    public ReviewView update(Long memberId, Long reviewId, ReviewChange change) {
        Review review = reviews.findForUpdate(reviewId)
            .orElseThrow(() -> new BusinessException(ReviewErrorCode.REVIEW_NOT_FOUND));
        int before = review.getRating();
        translateRule(() -> {
            review.update(memberId, change.toDomain());
            return null;
        });
        Review saved = reviews.saveAndFlush(review);
        int ratingDelta = saved.getRating() - before;
        if (ratingDelta != 0) {
            adjustStats(saved.getProductId(), 0, ratingDelta);
        }
        return ReviewView.from(saved);
    }

    @Transactional
    public void delete(Long memberId, Long reviewId) {
        Review review = reviews.findForUpdate(reviewId)
            .orElseThrow(() -> new BusinessException(ReviewErrorCode.REVIEW_NOT_FOUND));
        translateRule(() -> {
            review.checkAuthor(memberId);
            return null;
        });
        reviews.delete(review);
        adjustStats(review.getProductId(), -1, -review.getRating());
    }

    /**
     * 집계 행이 없으면(바뀐 행 0) 조용히 넘기지 않고 실패시킨다(트랜잭션이 되돌려져 리뷰 변경도 남지 않는다).
     * 메시지에는 상품 ID만 넣는다.
     */
    private void adjustStats(Long productId, int countDelta, int ratingDelta) {
        if (reviewStatsCounter.adjust(productId, countDelta, ratingDelta, ReviewTimes.now()) == 0) {
            throw new IllegalStateException("리뷰 집계 행이 없습니다: productId=" + productId);
        }
    }

    private static Long parseOrderItemId(String orderItemId) {
        try {
            long value = Long.parseLong(orderItemId);
            if (value > 0) {
                return value;
            }
        } catch (NumberFormatException e) {
            // 아래에서 같은 오류로 응답한다
        }
        throw new BusinessException(ReviewErrorCode.INVALID_ORDER_ITEM_ID);
    }

    private static <T> T translateRule(Supplier<T> work) {
        try {
            return work.get();
        } catch (ReviewRuleException e) {
            throw new BusinessException(errorOf(e.rule()));
        }
    }

    private static ReviewErrorCode errorOf(ReviewRule rule) {
        return switch (rule) {
            case RATING -> ReviewErrorCode.INVALID_RATING;
            case CONTENT -> ReviewErrorCode.INVALID_CONTENT;
            case AUTHOR -> ReviewErrorCode.REVIEW_FORBIDDEN;
            case NO_CHANGES -> ReviewErrorCode.NO_FIELDS_TO_UPDATE;
        };
    }

    /**
     * 수정 요청 값. given이 false면 그 값은 보내지 않은 것(그대로 둔다), true인데 값이 null이면 규칙 위반(400)이다.
     */
    public record ReviewChange(boolean ratingGiven, Integer rating, boolean contentGiven, String content) {

        Review.Change toDomain() {
            return new Review.Change(ratingGiven, rating, contentGiven, content);
        }
    }

    /**
     * 리뷰 한 건(응답용).
     */
    public record ReviewView(
        Long reviewId,
        Long orderItemId,
        Long productId,
        int rating,
        String content,
        String nickname,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

        static ReviewView from(Review review) {
            return new ReviewView(review.getId(), review.getOrderItemId(), review.getProductId(), review.getRating(),
                review.getContent(), review.getNickname(), review.getCreatedAt(), review.getUpdatedAt());
        }
    }
}
