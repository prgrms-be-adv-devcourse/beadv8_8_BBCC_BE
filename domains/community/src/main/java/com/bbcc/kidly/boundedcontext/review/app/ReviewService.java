package com.bbcc.kidly.boundedcontext.review.app;

import com.bbcc.kidly.boundedcontext.review.app.port.ReviewEligibilities;
import com.bbcc.kidly.boundedcontext.review.app.port.ReviewImageStorage;
import com.bbcc.kidly.boundedcontext.review.app.port.ReviewImages;
import com.bbcc.kidly.boundedcontext.review.app.port.ReviewStatsCounter;
import com.bbcc.kidly.boundedcontext.review.app.port.Reviews;
import com.bbcc.kidly.boundedcontext.review.domain.Fit;
import com.bbcc.kidly.boundedcontext.review.domain.Review;
import com.bbcc.kidly.boundedcontext.review.domain.ReviewEligibility;
import com.bbcc.kidly.boundedcontext.review.domain.ReviewImage;
import com.bbcc.kidly.boundedcontext.review.domain.ReviewImageUrls;
import com.bbcc.kidly.boundedcontext.review.domain.ReviewRule;
import com.bbcc.kidly.boundedcontext.review.domain.ReviewRuleException;
import com.bbcc.kidly.boundedcontext.review.domain.ReviewTimes;
import com.bbcc.kidly.global.error.BusinessException;
import java.time.LocalDateTime;
import java.util.List;
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
 *   <li>수정 · 삭제: 리뷰를 행 잠금으로 읽고 작성자를 확인한 뒤, 수정은 별점 차이만큼 · 삭제는 −1 · −별점으로 집계를 바꾼다.
 *       숨김 리뷰는 이미 집계에서 빠져 있어 바꾸지 않는다</li>
 *   <li>검사: 새 리뷰는 검사 대기(공개). 글 · 사진이 실제로 바뀐 수정은 다시 검사 대기(숨김 상태는 그대로)</li>
 *   <li>사진: 업로드 주소 발급으로 받은 본인 주소만 받는다(최대 장수는 설정 값). 작성은 리뷰와 같은 트랜잭션에서 저장하고,
 *       수정은 보내면 목록 전체를 바꾼다(지우고 새로 넣기). 리뷰를 지우면 DB가 사진 행도 지운다</li>
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
    private final ReviewImages reviewImages;
    private final ReviewImageStorage imageStorage;
    private final ReviewImageProperties imageProperties;

    /**
     * @param nickname 작성 시점 닉네임(로그인 정보). 없으면 null로 저장한다
     * @param orderItemId 리뷰 작성 가능 목록의 orderItemId(문자열)
     */
    public ReviewView write(Long memberId, String nickname, String orderItemId, Integer rating, String content) {
        return write(memberId, nickname, orderItemId, rating, content, FitInput.absent());
    }

    /**
     * @param fit 사이즈 핏(선택). 정해진 값이 아니면 INVALID_FIT, 구매 사이즈 없는 주문상품이면 FIT_NOT_APPLICABLE
     */
    public ReviewView write(Long memberId, String nickname, String orderItemId, Integer rating, String content,
        FitInput fit) {
        return write(memberId, nickname, orderItemId, rating, content, fit, ImageUrlsInput.absent());
    }

    /**
     * @param imageUrls 사진 주소(선택, 보낸 순서). 본인 주소가 아니면 INVALID_IMAGE_URL, 최대 장수를 넘으면 TOO_MANY_IMAGES.
     *     입력 확인이라 자격 확인보다 먼저 본다
     */
    public ReviewView write(Long memberId, String nickname, String orderItemId, Integer rating, String content,
        FitInput fit, ImageUrlsInput imageUrls) {
        Long itemId = parseOrderItemId(orderItemId);
        translateRule(() -> {
            Review.checkInput(rating, content);
            return null;
        });
        Fit fitValue = fit.toFit();
        ReviewImageUrls images = imageUrls.given() ? checkedImages(memberId, imageUrls) : ReviewImageUrls.none();
        ReviewEligibility eligibility = reviewEligibilities.findByOrderItemIdAndMemberId(itemId, memberId)
            .orElseThrow(() -> new BusinessException(ReviewErrorCode.REVIEW_NOT_ELIGIBLE));
        if (reviews.existsByOrderItemId(itemId)) {
            throw new BusinessException(ReviewErrorCode.REVIEW_ALREADY_WRITTEN);
        }
        String purchasedSize = eligibility.getSize();
        Review review = translateRule(() -> Review.create(new Review.Values(itemId, eligibility.getProductId(),
            memberId, nickname, rating, content, fitValue, purchasedSize != null)));
        try {
            return separateTransaction.call(() -> {
                Review saved = reviews.saveAndFlush(review);
                FitDelta fitDelta = FitDelta.of(null, saved.getFit());
                reviewStatsCounter.addReview(saved.getProductId(), saved.getRating(), fitDelta.small(),
                    fitDelta.trueToSize(), fitDelta.large(), ReviewTimes.now());
                reviewImages.saveAll(images.toImages(saved.getId()));
                return ReviewView.from(saved, purchasedSize, images.urls());
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
     * 보낸 값만 고친다. 집계는 별점이나 핏이 실제로 바뀔 때만 바뀐다(본문 · 사진만 바꾸거나 같은 값이면 그대로).
     * 사진을 보내면 목록 전체를 바꾼다(빈 목록이면 모두 지움). 작성자 확인이 값 확인보다 먼저고(남의 리뷰는 값이 틀려도 403),
     * 값을 모두 확인한 뒤에 바꾸므로 하나라도 틀리면 아무것도 바뀌지 않는다.
     * 오류: 400 NO_FIELDS_TO_UPDATE · INVALID_RATING · INVALID_CONTENT · INVALID_FIT · FIT_NOT_APPLICABLE ·
     * INVALID_IMAGE_URL · TOO_MANY_IMAGES, 403 REVIEW_FORBIDDEN, 404 REVIEW_NOT_FOUND.
     */
    @Transactional
    public ReviewView update(Long memberId, Long reviewId, ReviewChange change) {
        Review review = reviews.findForUpdate(reviewId)
            .orElseThrow(() -> new BusinessException(ReviewErrorCode.REVIEW_NOT_FOUND));
        translateRule(() -> {
            review.checkAuthor(memberId);
            return null;
        });
        Fit fitValue = change.fit().toFit();
        ReviewImageUrls images = change.images().given() ? checkedImages(memberId, change.images()) : null;
        String purchasedSize = purchasedSizeOf(review);
        int ratingBefore = review.getRating();
        Fit fitBefore = review.getFit();
        String contentBefore = review.getContent();
        List<String> imagesBefore = imageUrlsOf(review.getId());
        translateRule(() -> {
            review.update(memberId, change.toDomain(fitValue), purchasedSize != null);
            return null;
        });
        // 글 · 사진이 실제로 바뀌면 다시 검사 대기(공개 그대로). 별점 · 핏만 바뀌면 그대로
        if (!review.getContent().equals(contentBefore) || (images != null && !images.urls().equals(imagesBefore))) {
            review.markContentChanged();
        }
        Review saved = reviews.saveAndFlush(review);
        int ratingDelta = saved.getRating() - ratingBefore;
        FitDelta fitDelta = FitDelta.of(fitBefore, saved.getFit());
        // 숨김 리뷰는 이미 집계에서 빠져 있다. 공개로 돌아올 때 그때 값으로 더한다
        if (saved.isPublic() && (ratingDelta != 0 || !fitDelta.isZero())) {
            adjustStats(saved.getProductId(), 0, ratingDelta, fitDelta);
        }
        List<String> imageUrls = imagesBefore;
        if (images != null) {
            reviewImages.deleteAllOfReview(saved.getId());
            reviewImages.saveAll(images.toImages(saved.getId()));
            imageUrls = images.urls();
        }
        return ReviewView.from(saved, purchasedSize, imageUrls);
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
        // 숨김 리뷰는 이미 집계에서 빠져 있다
        if (review.isPublic()) {
            adjustStats(review.getProductId(), -1, -review.getRating(), FitDelta.of(review.getFit(), null));
        }
    }

    /**
     * 사진 주소를 확인한다: 장수(설정 값) 먼저, 다음에 본인 주소(본인 경로 · 서버가 만든 이름 · 중복 없음). 틀린 타입도 INVALID_IMAGE_URL.
     */
    private ReviewImageUrls checkedImages(Long memberId, ImageUrlsInput input) {
        if (!input.valid()) {
            throw new BusinessException(ReviewErrorCode.INVALID_IMAGE_URL);
        }
        int maxCount = imageProperties.maxCount();
        try {
            return ReviewImageUrls.of(input.urls(), imageStorage.ownImageUrlPrefix(memberId), maxCount);
        } catch (ReviewRuleException e) {
            if (e.rule() == ReviewRule.TOO_MANY_IMAGES) {
                throw new BusinessException(ReviewErrorCode.TOO_MANY_IMAGES,
                    ReviewErrorCode.TOO_MANY_IMAGES.detailWith(maxCount));
            }
            throw new BusinessException(errorOf(e.rule()));
        }
    }

    private List<String> imageUrlsOf(Long reviewId) {
        return reviewImages.findByReviewIdOrderByPositionAsc(reviewId).stream().map(ReviewImage::getImageUrl).toList();
    }

    // 구매 사이즈는 작성 자격에 저장된 값을 쓴다(리뷰에 따로 저장하지 않는다)
    private String purchasedSizeOf(Review review) {
        return reviewEligibilities.findByOrderItemIdAndMemberId(review.getOrderItemId(), review.getMemberId())
            .map(ReviewEligibility::getSize)
            .orElse(null);
    }

    /**
     * 집계 행이 없으면(바뀐 행 0) 조용히 넘기지 않고 실패시킨다(트랜잭션이 되돌려져 리뷰 변경도 남지 않는다).
     * 메시지에는 상품 ID만 넣는다.
     */
    private void adjustStats(Long productId, int countDelta, int ratingDelta, FitDelta fit) {
        if (reviewStatsCounter.adjust(productId, countDelta, ratingDelta, fit.small(), fit.trueToSize(), fit.large(),
            ReviewTimes.now()) == 0) {
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
            case FIT_NOT_APPLICABLE -> ReviewErrorCode.FIT_NOT_APPLICABLE;
            case IMAGE_FILE -> ReviewErrorCode.INVALID_IMAGE_FILE;
            case TOO_MANY_IMAGES -> ReviewErrorCode.TOO_MANY_IMAGES;
            case IMAGE_URL -> ReviewErrorCode.INVALID_IMAGE_URL;
        };
    }

    /**
     * 수정 요청 값. given이 false면 그 값은 보내지 않은 것(그대로 둔다), true인데 값이 null이면 규칙 위반(400)이다.
     */
    public record ReviewChange(boolean ratingGiven, Integer rating, boolean contentGiven, String content,
                               FitInput fit, ImageUrlsInput images) {

        /**
         * 사진은 바꾸지 않는다.
         */
        public ReviewChange(boolean ratingGiven, Integer rating, boolean contentGiven, String content,
            FitInput fit) {
            this(ratingGiven, rating, contentGiven, content, fit, ImageUrlsInput.absent());
        }

        /**
         * 핏 · 사진은 바꾸지 않는다.
         */
        public ReviewChange(boolean ratingGiven, Integer rating, boolean contentGiven, String content) {
            this(ratingGiven, rating, contentGiven, content, FitInput.absent());
        }

        Review.Change toDomain(Fit fitValue) {
            return new Review.Change(ratingGiven, rating, contentGiven, content, fit.given(), fitValue,
                images.given());
        }
    }

    /**
     * 요청의 사진 주소 목록. 키가 없으면 absent(작성은 사진 없음, 수정은 그대로), 문자열 목록이면 of(빈 목록 = 사진 없음 · 모두 지움),
     * 목록이 아니거나 문자열 아닌 항목이 있으면 invalid(INVALID_IMAGE_URL).
     */
    public record ImageUrlsInput(boolean given, boolean valid, List<String> urls) {

        public static ImageUrlsInput absent() {
            return new ImageUrlsInput(false, true, List.of());
        }

        public static ImageUrlsInput of(List<String> urls) {
            return new ImageUrlsInput(true, true, urls);
        }

        public static ImageUrlsInput invalid() {
            return new ImageUrlsInput(true, false, List.of());
        }
    }

    /**
     * 요청의 핏 값. 키가 없으면 absent(그대로 둠), null을 보냈으면 cleared(핏을 지움), 문자열이면 text, 그 밖의 타입이면 invalid.
     * 값은 SMALL · TRUE_TO_SIZE · LARGE와 정확히 같아야 한다(대소문자 포함, 아니면 INVALID_FIT).
     */
    public record FitInput(boolean given, boolean valid, String text) {

        public static FitInput absent() {
            return new FitInput(false, true, null);
        }

        public static FitInput cleared() {
            return new FitInput(true, true, null);
        }

        public static FitInput text(String text) {
            return new FitInput(true, true, text);
        }

        public static FitInput invalid() {
            return new FitInput(true, false, null);
        }

        Fit toFit() {
            if (!valid) {
                throw new BusinessException(ReviewErrorCode.INVALID_FIT);
            }
            if (text == null) {
                return null;
            }
            try {
                return Fit.valueOf(text);
            } catch (IllegalArgumentException e) {
                throw new BusinessException(ReviewErrorCode.INVALID_FIT);
            }
        }
    }

    /**
     * 핏별 수 증감(이전 값 −1 · 새 값 +1, 같으면 0).
     */
    record FitDelta(int small, int trueToSize, int large) {

        static FitDelta of(Fit before, Fit after) {
            if (before == after) {
                return new FitDelta(0, 0, 0);
            }
            int[] delta = new int[3];
            if (before != null) {
                delta[before.ordinal()]--;
            }
            if (after != null) {
                delta[after.ordinal()]++;
            }
            return new FitDelta(delta[Fit.SMALL.ordinal()], delta[Fit.TRUE_TO_SIZE.ordinal()],
                delta[Fit.LARGE.ordinal()]);
        }

        boolean isZero() {
            return small == 0 && trueToSize == 0 && large == 0;
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
        boolean authorWithdrawn,
        String fit,
        String purchasedSize,
        List<String> imageUrls,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

        static ReviewView from(Review review, String purchasedSize, List<String> imageUrls) {
            boolean withdrawn = review.isAuthorWithdrawn();
            return new ReviewView(review.getId(), review.getOrderItemId(), review.getProductId(), review.getRating(),
                review.getContent(), AuthorDisplay.nickname(review.getNickname(), withdrawn), withdrawn,
                review.getFit() == null ? null : review.getFit().name(), purchasedSize, List.copyOf(imageUrls),
                review.getCreatedAt(), review.getUpdatedAt());
        }
    }
}
