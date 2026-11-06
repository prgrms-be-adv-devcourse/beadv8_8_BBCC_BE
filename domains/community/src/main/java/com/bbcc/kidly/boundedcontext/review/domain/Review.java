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
                         boolean fitGiven, Fit fit) {

        /**
         * 핏은 바꾸지 않는다.
         */
        public Change(boolean ratingGiven, Integer rating, boolean contentGiven, String content) {
            this(ratingGiven, rating, contentGiven, content, false, null);
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
     *
     * @param sizeApplicable 구매 사이즈가 있는 주문상품인지(아니면 핏을 넣을 수 없다. 지우는 것은 된다)
     */
    public void update(Long memberId, Change change, boolean sizeApplicable) {
        checkAuthor(memberId);
        if (!change.ratingGiven() && !change.contentGiven() && !change.fitGiven()) {
            throw new ReviewRuleException(ReviewRule.NO_CHANGES);
        }
        int newRating = change.ratingGiven() ? validRating(change.rating()) : this.rating;
        String newContent = change.contentGiven() ? validContent(change.content()) : this.content;
        Fit newFit = change.fitGiven() ? validFit(change.fit(), sizeApplicable) : this.fit;
        this.rating = (short) newRating;
        this.content = newContent;
        this.fit = newFit;
    }

    public int getRating() {
        return rating;
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
