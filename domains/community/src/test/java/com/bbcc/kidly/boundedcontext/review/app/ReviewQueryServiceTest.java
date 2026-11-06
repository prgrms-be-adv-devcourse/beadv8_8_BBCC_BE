package com.bbcc.kidly.boundedcontext.review.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.review.app.ReviewQueryService.ReviewPage;
import com.bbcc.kidly.boundedcontext.review.app.port.ReviewImages;
import com.bbcc.kidly.boundedcontext.review.app.port.ReviewStatsReader;
import com.bbcc.kidly.boundedcontext.review.app.port.Reviews;
import com.bbcc.kidly.boundedcontext.review.domain.DisplayStatus;
import com.bbcc.kidly.boundedcontext.review.domain.Fit;
import com.bbcc.kidly.boundedcontext.review.domain.ModerationStatus;
import com.bbcc.kidly.boundedcontext.review.domain.Review;
import com.bbcc.kidly.boundedcontext.review.domain.ReviewImage;
import com.bbcc.kidly.boundedcontext.review.domain.ReviewStats;
import com.bbcc.kidly.global.error.BusinessException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.Limit;
import org.springframework.test.util.ReflectionTestUtils;

class ReviewQueryServiceTest {

    private static final LocalDateTime BASE = LocalDateTime.of(2026, 11, 2, 10, 0, 0, 123_456_123);

    private final FakeReviews reviews = new FakeReviews();
    private final FakeStats stats = new FakeStats();
    private final FakeImages images = new FakeImages();
    private final ReviewQueryService service = new ReviewQueryService(reviews, stats, images);

    @ParameterizedTest
    @ValueSource(strings = {"abc", "", "0", "-1", "1.5"})
    @NullSource
    @DisplayName("productId가 없거나 양의 정수가 아니면 INVALID_PRODUCT_ID(목록 · 요약)")
    void invalidProductId(String productId) {
        assertError(() -> service.findByProduct(productId, null, null), ReviewErrorCode.INVALID_PRODUCT_ID);
        assertError(() -> service.summary(productId), ReviewErrorCode.INVALID_PRODUCT_ID);
    }

    @Test
    @DisplayName("size · cursor 검사는 리뷰 작성 자격 목록과 같다(INVALID_PAGE_SIZE · INVALID_CURSOR)")
    void sizeAndCursor() {
        assertError(() -> service.findByProduct("1", null, "51"), ReviewErrorCode.INVALID_PAGE_SIZE);
        assertError(() -> service.findMine(1L, "!!", null), ReviewErrorCode.INVALID_CURSOR);
        service.findByProduct("1", null, null);
        assertThat(reviews.lastLimit).isEqualTo(21);
    }

    @Test
    @DisplayName("size보다 많으면 마지막 항목 커서를 주고, 그 커서로 다음 쪽을 그 항목 뒤부터 읽는다. 마지막 쪽은 null")
    void cursorRoundTrip() {
        reviews.add(3);

        ReviewPage first = service.findByProduct("1", null, "2");
        service.findByProduct("1", first.nextCursor(), "2");

        assertThat(first.items()).hasSize(2);
        assertThat(reviews.lastAfter)
            .containsExactly(first.items().get(1).createdAt(), first.items().get(1).reviewId());
        assertThat(service.findMine(1L, null, "3").nextCursor()).isNull();
    }

    @Test
    @DisplayName("사진: 한 쪽 리뷰 ID로 한 번에 읽어 항목마다 순서대로 붙이고(없으면 빈 목록), 다음 쪽 확인용 리뷰는 묻지 않는다")
    void imagesPerPage() {
        reviews.add(3);
        images.add(99L, 1, "b.png");
        images.add(100L, 0, "x.jpg");
        images.add(99L, 0, "a.png");

        ReviewPage page = service.findByProduct("1", null, "2");

        assertThat(images.calls).containsExactly(List.of(100L, 99L));
        assertThat(page.items()).extracting(ReviewQueryService.ReviewItem::imageUrls)
            .containsExactly(List.of("x.jpg"), List.of("a.png", "b.png"));
        assertThat(service.findMine(1L, null, "1").items().getFirst().imageUrls()).containsExactly("x.jpg");
        images.calls.clear();
        // 빈 쪽은 사진을 묻지 않는다
        assertThat(service.findByProduct("1", page.nextCursor(), "2").items()).isEmpty();
        assertThat(images.calls).isEmpty();
    }

    @Test
    @DisplayName("visibility: 숨김 두 상태는 UNDER_REVIEW, 나머지는 PUBLIC(이유 분류는 담지 않는다)")
    void visibility() {
        reviews.add(3);

        assertThat(service.findMine(1L, null, "3").items())
            .extracting(ReviewQueryService.ReviewItem::reviewId, ReviewQueryService.ReviewItem::visibility)
            .containsExactly(org.assertj.core.groups.Tuple.tuple(100L, "PUBLIC"),
                org.assertj.core.groups.Tuple.tuple(99L, "UNDER_REVIEW"),
                org.assertj.core.groups.Tuple.tuple(98L, "PUBLIC"));
    }

    @Test
    @DisplayName("요약: 집계 행이 있으면 수 · 평균, 없으면 0 · null")
    void summary() {
        stats.row = stats(3, 13);

        assertThat(service.summary("1").averageRating()).isEqualTo(new BigDecimal("4.3"));
        assertThat(service.summary("1").reviewCount()).isEqualTo(3);
        stats.row = null;
        assertThat(service.summary("1").averageRating()).isNull();
        assertThat(service.summary("1").reviewCount()).isZero();
    }

    private static ReviewStats stats(long count, long sum) {
        ReviewStats row = org.springframework.beans.BeanUtils.instantiateClass(ReviewStats.class);
        ReflectionTestUtils.setField(row, "productId", 1L);
        ReflectionTestUtils.setField(row, "reviewCount", count);
        ReflectionTestUtils.setField(row, "ratingSum", sum);
        return row;
    }

    private static void assertError(Runnable call, ReviewErrorCode code) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(code));
    }

    private static final class FakeStats implements ReviewStatsReader {

        private ReviewStats row;

        @Override
        public Optional<ReviewStats> findById(Long productId) {
            return Optional.ofNullable(row);
        }
    }

    private static final class FakeImages implements ReviewImages {

        private final List<ReviewImage> rows = new ArrayList<>();
        private final List<List<Long>> calls = new ArrayList<>();

        void add(long reviewId, int position, String url) {
            rows.add(ReviewImage.of(reviewId, position, url));
        }

        @Override
        public List<ReviewImage> findByReviewIdInOrderByReviewIdAscPositionAsc(Collection<Long> reviewIds) {
            calls.add(List.copyOf(reviewIds));
            return rows.stream().filter(image -> reviewIds.contains(image.getReviewId()))
                .sorted(Comparator.comparing(ReviewImage::getReviewId).thenComparing(ReviewImage::getPosition))
                .toList();
        }

        @Override
        public <S extends ReviewImage> List<S> saveAll(Iterable<S> images) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<ReviewImage> findByReviewIdOrderByPositionAsc(Long reviewId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int deleteAllOfReview(Long reviewId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean existsByImageUrl(String imageUrl) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<String> findAttachedUrls(Collection<String> imageUrls) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class FakeReviews implements Reviews {

        private final List<ReviewListRow> data = new ArrayList<>();
        private int lastLimit;
        private List<Object> lastAfter;

        void add(int count) {
            for (int i = 0; i < count; i++) {
                data.add(new Row(100L - i, BASE.minusMinutes(i)));
            }
        }

        @Override
        public List<ReviewListRow> findByProduct(Long productId, Limit limit) {
            lastLimit = limit.max();
            return data.stream().limit(limit.max()).toList();
        }

        @Override
        public List<ReviewListRow> findByProductAfter(Long productId, LocalDateTime createdAt, Long id,
            Limit limit) {
            lastAfter = List.of(createdAt, id);
            return List.of();
        }

        @Override
        public List<ReviewListRow> findByMember(Long memberId, Limit limit) {
            return data.stream().limit(limit.max()).toList();
        }

        @Override
        public List<ReviewListRow> findByMemberAfter(Long memberId, LocalDateTime createdAt, Long id, Limit limit) {
            return List.of();
        }

        @Override
        public boolean existsByOrderItemId(Long orderItemId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Review saveAndFlush(Review review) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Review> findForUpdate(Long reviewId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Review> findById(Long reviewId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void delete(Review review) {
            throw new UnsupportedOperationException();
        }
    }

    private record Row(Long id, LocalDateTime at) implements Reviews.ReviewListRow {

        @Override
        public Long getId() {
            return id;
        }

        @Override
        public Long getOrderItemId() {
            return id;
        }

        @Override
        public Long getProductId() {
            return 1L;
        }

        @Override
        public String getProductName() {
            return null;
        }

        @Override
        public String getThumbnailUrl() {
            return null;
        }

        @Override
        public ModerationStatus getModerationStatus() {
            return id == 99L ? ModerationStatus.HIDDEN : ModerationStatus.PASSED;
        }

        @Override
        public DisplayStatus getDisplayStatus() {
            return null;
        }

        @Override
        public Number getRating() {
            return 5;
        }

        @Override
        public String getContent() {
            return "본문";
        }

        @Override
        public String getNickname() {
            return "닉";
        }

        @Override
        public LocalDateTime getAuthorWithdrawnAt() {
            return null;
        }

        @Override
        public Fit getFit() {
            return null;
        }

        @Override
        public String getPurchasedSize() {
            return null;
        }

        @Override
        public LocalDateTime getCreatedAt() {
            return at;
        }

        @Override
        public LocalDateTime getUpdatedAt() {
            return at;
        }
    }
}
