package com.bbcc.kidly.boundedcontext.review.fixture;

import java.time.LocalDateTime;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 리뷰 컨텍스트 표(상품 복사본 · 작성 자격 · 리뷰 · 리뷰 요약 · 리뷰 사진)에 테스트 행을 넣는다. 서비스를 거치지 않고 상태를 만들 때 쓴다.
 * 예: {@code rows.review(11, 14, ME).status("HIDDEN").reason("HATE").insert()}.
 */
public final class ReviewRows {

    private final JdbcTemplate jdbc;

    public ReviewRows(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 상품 복사본. 기본: 이름 '상품', 썸네일 없음, 판매 중(ON_SALE), 반영 · 생성 · 수정 시각 now().
     */
    public ReplicaProductRow replicaProduct(long productId) {
        return new ReplicaProductRow(jdbc, productId);
    }

    /**
     * 작성 자격. 기본: 주문 1, 옵션 1, 구매 사이즈 없음, 구매확정 · 생성 · 수정 시각 now().
     */
    public EligibilityRow eligibility(long orderItemId, long memberId, long productId) {
        return new EligibilityRow(jdbc, orderItemId, memberId, productId);
    }

    /**
     * 리뷰. 기본: 닉네임 '닉', 별점 4, 본문 '본문', 생성 · 수정 시각 now(), 검사 상태 PASSED(DB 기본값이 없어 늘 넣는다).
     */
    public ReviewRow review(long orderItemId, long productId, long memberId) {
        return new ReviewRow(jdbc, orderItemId, productId, memberId);
    }

    /**
     * 상품 리뷰 요약. 기본: 생성 · 수정 시각 now(), 핏별 수는 DB 기본값(0).
     */
    public ReviewStatsRow reviewStats(long productId, int reviewCount, int ratingSum) {
        return new ReviewStatsRow(jdbc, productId, reviewCount, ratingSum);
    }

    /**
     * 리뷰 사진 한 장. 기본: 생성 시각 now().
     */
    public ReviewImageRow reviewImage(long reviewId, int position, String imageUrl) {
        return new ReviewImageRow(jdbc, reviewId, position, imageUrl);
    }

    public static final class ReplicaProductRow extends InsertRow<ReplicaProductRow> {

        private ReplicaProductRow(JdbcTemplate jdbc, long productId) {
            super(jdbc, "replica_product");
            set("product_id", productId);
            set("name", "상품");
            set("thumbnail_url", null);
            set("display_status", "ON_SALE");
            now("source_occurred_at");
            now("created_at");
            now("updated_at");
        }

        public ReplicaProductRow name(String name) {
            return set("name", name);
        }

        public ReplicaProductRow thumbnailUrl(String thumbnailUrl) {
            return set("thumbnail_url", thumbnailUrl);
        }

        public ReplicaProductRow status(String displayStatus) {
            return set("display_status", displayStatus);
        }

        public ReplicaProductRow occurredAt(LocalDateTime occurredAt) {
            return set("source_occurred_at", occurredAt);
        }

        public ReplicaProductRow createdAt(LocalDateTime createdAt) {
            return set("created_at", createdAt);
        }

        public ReplicaProductRow updatedAt(LocalDateTime updatedAt) {
            return set("updated_at", updatedAt);
        }

        /**
         * 반영 · 생성 · 수정 시각을 모두 같은 값으로.
         */
        public ReplicaProductRow at(LocalDateTime at) {
            return occurredAt(at).createdAt(at).updatedAt(at);
        }

        @Override
        protected ReplicaProductRow self() {
            return this;
        }
    }

    public static final class EligibilityRow extends InsertRow<EligibilityRow> {

        private EligibilityRow(JdbcTemplate jdbc, long orderItemId, long memberId, long productId) {
            super(jdbc, "review_eligibility");
            set("order_item_id", orderItemId);
            set("order_id", 1L);
            set("member_id", memberId);
            set("product_id", productId);
            set("option_id", 1L);
            set("size", null);
            now("confirmed_at");
            now("created_at");
            now("updated_at");
        }

        public EligibilityRow orderId(long orderId) {
            return set("order_id", orderId);
        }

        public EligibilityRow optionId(long optionId) {
            return set("option_id", optionId);
        }

        /**
         * 구매 사이즈(없으면 null, 핏을 쓸 수 없는 주문상품).
         */
        public EligibilityRow size(String size) {
            return set("size", size);
        }

        public EligibilityRow confirmedAt(LocalDateTime confirmedAt) {
            return set("confirmed_at", confirmedAt);
        }

        /**
         * 생성 · 수정 시각을 같은 값으로.
         */
        public EligibilityRow at(LocalDateTime at) {
            return set("created_at", at).set("updated_at", at);
        }

        @Override
        protected EligibilityRow self() {
            return this;
        }
    }

    public static final class ReviewRow extends InsertRow<ReviewRow> {

        private ReviewRow(JdbcTemplate jdbc, long orderItemId, long productId, long memberId) {
            super(jdbc, "review");
            set("order_item_id", orderItemId);
            set("product_id", productId);
            set("member_id", memberId);
            set("nickname", "닉");
            set("rating", 4);
            set("content", "본문");
            now("created_at");
            now("updated_at");
            set("moderation_status", "PASSED");
        }

        public ReviewRow nickname(String nickname) {
            return set("nickname", nickname);
        }

        public ReviewRow rating(int rating) {
            return set("rating", rating);
        }

        public ReviewRow content(String content) {
            return set("content", content);
        }

        /**
         * 사이즈 핏 이름(SMALL · TRUE_TO_SIZE · LARGE).
         */
        public ReviewRow fit(String fit) {
            return set("fit", fit);
        }

        public ReviewRow createdAt(LocalDateTime createdAt) {
            return set("created_at", createdAt);
        }

        public ReviewRow updatedAt(LocalDateTime updatedAt) {
            return set("updated_at", updatedAt);
        }

        /**
         * 생성 · 수정 시각을 같은 값으로.
         */
        public ReviewRow at(LocalDateTime at) {
            return createdAt(at).updatedAt(at);
        }

        /**
         * 검사 상태 이름(PENDING · PASSED · HIDDEN · CONFIRMED_HIDDEN · RESTORED).
         */
        public ReviewRow status(String moderationStatus) {
            return set("moderation_status", moderationStatus);
        }

        public ReviewRow attempts(int moderationAttempts) {
            return set("moderation_attempts", moderationAttempts);
        }

        /**
         * 걸린 이유 분류 이름(없으면 null).
         */
        public ReviewRow reason(String moderationReason) {
            return set("moderation_reason", moderationReason);
        }

        public ReviewRow moderatedAt(LocalDateTime moderatedAt) {
            return set("moderated_at", moderatedAt);
        }

        public ReviewRow contentVersion(long contentVersion) {
            return set("content_version", contentVersion);
        }

        public ReviewRow authorWithdrawnAt(LocalDateTime authorWithdrawnAt) {
            return set("author_withdrawn_at", authorWithdrawnAt);
        }

        /**
         * 넣고 리뷰 ID를 돌려준다.
         */
        public long insertId() {
            return insertReturningId();
        }

        @Override
        protected ReviewRow self() {
            return this;
        }
    }

    public static final class ReviewStatsRow extends InsertRow<ReviewStatsRow> {

        private ReviewStatsRow(JdbcTemplate jdbc, long productId, int reviewCount, int ratingSum) {
            super(jdbc, "review_stats");
            set("product_id", productId);
            set("review_count", reviewCount);
            set("rating_sum", ratingSum);
            now("created_at");
            now("updated_at");
        }

        public ReviewStatsRow fits(int small, int trueToSize, int large) {
            return set("fit_small", small).set("fit_true_to_size", trueToSize).set("fit_large", large);
        }

        /**
         * 생성 · 수정 시각을 같은 값으로.
         */
        public ReviewStatsRow at(LocalDateTime at) {
            return set("created_at", at).set("updated_at", at);
        }

        @Override
        protected ReviewStatsRow self() {
            return this;
        }
    }

    public static final class ReviewImageRow extends InsertRow<ReviewImageRow> {

        private ReviewImageRow(JdbcTemplate jdbc, long reviewId, int position, String imageUrl) {
            super(jdbc, "review_image");
            set("review_id", reviewId);
            set("position", position);
            set("image_url", imageUrl);
            now("created_at");
        }

        public ReviewImageRow at(LocalDateTime at) {
            return set("created_at", at);
        }

        @Override
        protected ReviewImageRow self() {
            return this;
        }
    }
}
