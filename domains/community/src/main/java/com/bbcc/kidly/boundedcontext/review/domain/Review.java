package com.bbcc.kidly.boundedcontext.review.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 리뷰(주문상품당 1개). 작성자 닉네임은 작성 시점 값을 저장한다. 수정 · 삭제는 작성자만 한다.
 */
@Entity(name = "CommunityReview")
@Table(schema = "community", name = "review")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Review extends ReviewTimeEntity {

    static final int MIN_RATING = 1;
    static final int MAX_RATING = 5;
    static final int MAX_CONTENT_LENGTH = 1000;

    /**
     * 작성 값.
     *
     * @param nickname 작성 시점 닉네임. 모르면 null
     * @param rating 1~5
     * @param content 앞뒤 공백을 뺀 1~1000자. 앞뒤 공백은 잘라 저장한다
     */
    public record Values(Long orderItemId, Long productId, Long memberId, String nickname, Integer rating,
                         String content, Fit fit, boolean sizeApplicable) {

        /**
         * 핏 없이 쓴다.
         */
        public Values(Long orderItemId, Long productId, Long memberId, String nickname, Integer rating,
            String content) {
            this(orderItemId, productId, memberId, nickname, rating, content, null, false);
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long orderItemId;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private Long memberId;

    private String nickname;

    // 표 컬럼이 smallint(1~5)라 short로 둔다. 밖에는 int로 보인다(getRating)
    @Getter(AccessLevel.NONE)
    @Column(nullable = false)
    private short rating;

    @Column(nullable = false, length = MAX_CONTENT_LENGTH)
    private String content;

    // 작성자가 탈퇴한 시각. 탈퇴하지 않았으면 null. 탈퇴하면 닉네임을 지운다
    private LocalDateTime authorWithdrawnAt;

    // 사이즈 핏. 남기지 않았으면 null
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Fit fit;

    // 검사 상태. 새 리뷰는 검사 대기(공개)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ModerationStatus moderationStatus = ModerationStatus.PENDING;

    // 검사에서 걸린 이유. 걸리지 않았으면 null
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ModerationReason moderationReason;

    // 판단 못 한 검사 횟수(재시도 상한과 비교). 글 · 사진이 바뀌면 0
    @Column(nullable = false)
    private int moderationAttempts;

    // 마지막으로 검사 결과 · 관리자 조치가 반영된 시각
    private LocalDateTime moderatedAt;

    // 글 · 사진이 바뀔 때마다 +1. 검사 결과는 읽을 때의 버전이 그대로일 때만 반영한다
    @Column(nullable = false)
    private long contentVersion;

    // 숨김 확정 · 다시 공개를 한 관리자
    private Long moderatedByAdminId;

    public static Review create(Values values) {
        int rating = validRating(values.rating());
        String content = validContent(values.content());
        Fit fit = validFit(values.fit(), values.sizeApplicable());
        Review review = new Review();
        review.orderItemId = values.orderItemId();
        review.productId = values.productId();
        review.memberId = values.memberId();
        review.nickname = values.nickname();
        review.rating = (short) rating;
        review.content = content;
        review.fit = fit;
        return review;
    }

    /**
     * 별점 · 본문 규칙을 확인한다(저장 전에 입력만 먼저 볼 때).
     */
    public static void checkInput(Integer rating, String content) {
        validRating(rating);
        validContent(content);
    }

    /**
     * 수정할 값. 보낸 값만 바꾼다(given이 false면 그 값은 그대로). 보냈는데 null이면 그 값의 규칙 위반이다.
     */
    public record Change(boolean ratingGiven, Integer rating, boolean contentGiven, String content,
                         boolean fitGiven, Fit fit, boolean imagesGiven) {

        /**
         * 사진은 바꾸지 않는다.
         */
        public Change(boolean ratingGiven, Integer rating, boolean contentGiven, String content,
            boolean fitGiven, Fit fit) {
            this(ratingGiven, rating, contentGiven, content, fitGiven, fit, false);
        }

        /**
         * 핏 · 사진은 바꾸지 않는다.
         */
        public Change(boolean ratingGiven, Integer rating, boolean contentGiven, String content) {
            this(ratingGiven, rating, contentGiven, content, false, null);
        }

        /**
         * 사진만 바꾼다(사진 목록은 리뷰 밖 사진 행에 있다).
         */
        public static Change imagesOnly() {
            return new Change(false, null, false, null, false, null, true);
        }

        public static Change both(Integer rating, String content) {
            return new Change(true, rating, true, content);
        }

        public static Change ratingOnly(Integer rating) {
            return new Change(true, rating, false, null);
        }

        public static Change contentOnly(String content) {
            return new Change(false, null, true, content);
        }

        /**
         * 핏만 바꾼다. null이면 핏을 지운다.
         */
        public static Change fitOnly(Fit fit) {
            return new Change(false, null, false, null, true, fit);
        }
    }

    /**
     * 작성자가 별점 · 본문을 고친다.
     */
    public void update(Long memberId, Integer rating, String content) {
        update(memberId, Change.both(rating, content));
    }

    /**
     * 작성자가 보낸 값만 고친다. 바꿀 값이 없거나 규칙 위반이면 아무것도 바꾸지 않는다.
     */
    public void update(Long memberId, Change change) {
        update(memberId, change, false);
    }

    /**
     * 작성자가 보낸 값만 고친다. 바꿀 값이 없거나 규칙 위반이면 아무것도 바꾸지 않는다.
     * 사진을 보냈으면(사진 행은 밖에서 바꾼다) 다른 값이 그대로여도 수정 시각을 바꾼다.
     *
     * @param sizeApplicable 구매 사이즈가 있는 주문상품인지(아니면 핏을 넣을 수 없다. 지우는 것은 된다)
     */
    public void update(Long memberId, Change change, boolean sizeApplicable) {
        checkAuthor(memberId);
        if (!change.ratingGiven() && !change.contentGiven() && !change.fitGiven() && !change.imagesGiven()) {
            throw new ReviewRuleException(ReviewRule.NO_CHANGES);
        }
        int newRating = change.ratingGiven() ? validRating(change.rating()) : this.rating;
        String newContent = change.contentGiven() ? validContent(change.content()) : this.content;
        Fit newFit = change.fitGiven() ? validFit(change.fit(), sizeApplicable) : this.fit;
        this.rating = (short) newRating;
        this.content = newContent;
        this.fit = newFit;
        if (change.imagesGiven()) {
            touch();
        }
    }

    public int getRating() {
        return rating;
    }

    /**
     * 공개 목록 · 상품 요약에 들어가는지(검사 대기 · 통과 · 다시 공개).
     */
    public boolean isPublic() {
        return moderationStatus.isPublic();
    }

    /**
     * 글 · 사진이 바뀌었다. 버전을 올리고, 공개 상태면 다시 검사 대기로 둔다(공개는 그대로).
     * 숨김 상태(검사 숨김 · 숨김 확정)는 관리자 판단이 남아 있어 그대로 둔다.
     */
    public void markContentChanged() {
        contentVersion++;
        if (moderationStatus.isPublic()) {
            moderationStatus = ModerationStatus.PENDING;
            moderationReason = null;
            moderationAttempts = 0;
        }
    }

    /**
     * 검사 결과를 받을 수 있는지: 아직 대기이고, 검사기에 넘길 때 읽은 버전 · 시도 횟수가 그대로다.
     * 그 사이 글이 바뀌었거나 관리자 조치 · 다른 처리기의 반영이 있었으면 그 결과는 버린다.
     */
    public boolean isAwaitingModeration(long expectedVersion, int expectedAttempts) {
        return moderationStatus == ModerationStatus.PENDING && contentVersion == expectedVersion
            && moderationAttempts == expectedAttempts;
    }

    public void pass(LocalDateTime now) {
        moderationStatus = ModerationStatus.PASSED;
        moderationReason = null;
        moderatedAt = ReviewTimes.toStoredPrecision(now);
    }

    /**
     * 검사에서 걸려 숨긴다(공개 → 숨김, 집계에서 뺀다).
     */
    public void hideByModeration(ModerationReason reason, LocalDateTime now) {
        moderationStatus = ModerationStatus.HIDDEN;
        moderationReason = reason;
        moderatedAt = ReviewTimes.toStoredPrecision(now);
    }

    /**
     * 검사기가 판단하지 못했다(장애 · 시간 초과 · 한도 초과). 대기 그대로 다음 주기에 다시 검사한다.
     */
    public void recordUndecided() {
        moderationAttempts++;
    }

    /**
     * 관리자 숨김 확정.
     *
     * @return 공개였다가 숨겨졌는지(그 리뷰 몫을 집계에서 뺀다). 이미 숨김이면 false
     */
    public boolean hideByAdmin(Long adminId, LocalDateTime now) {
        boolean wasPublic = isPublic();
        moderationStatus = ModerationStatus.CONFIRMED_HIDDEN;
        moderatedByAdminId = adminId;
        moderatedAt = ReviewTimes.toStoredPrecision(now);
        return wasPublic;
    }

    /**
     * 관리자 다시 공개.
     *
     * @return 숨김이었다가 공개됐는지(그때 값으로 집계에 더한다). 이미 공개면 false
     */
    public boolean restoreByAdmin(Long adminId, LocalDateTime now) {
        boolean wasHidden = !isPublic();
        moderationStatus = ModerationStatus.RESTORED;
        moderatedByAdminId = adminId;
        moderatedAt = ReviewTimes.toStoredPrecision(now);
        return wasHidden;
    }

    public boolean isAuthorWithdrawn() {
        return authorWithdrawnAt != null;
    }

    public void checkAuthor(Long memberId) {
        if (!this.memberId.equals(memberId)) {
            throw new ReviewRuleException(ReviewRule.AUTHOR);
        }
    }

    private static int validRating(Integer rating) {
        if (rating == null || rating < MIN_RATING || rating > MAX_RATING) {
            throw new ReviewRuleException(ReviewRule.RATING);
        }
        return rating;
    }

    private static Fit validFit(Fit fit, boolean sizeApplicable) {
        if (fit != null && !sizeApplicable) {
            throw new ReviewRuleException(ReviewRule.FIT_NOT_APPLICABLE);
        }
        return fit;
    }

    private static String validContent(String content) {
        String trimmed = content == null ? "" : content.strip();
        if (trimmed.isEmpty() || trimmed.length() > MAX_CONTENT_LENGTH) {
            throw new ReviewRuleException(ReviewRule.CONTENT);
        }
        return trimmed;
    }
}
