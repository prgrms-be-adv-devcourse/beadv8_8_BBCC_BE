package com.bbcc.kidly.boundedcontext.review.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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
                         String content) {
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

    public static Review create(Values values) {
        int rating = validRating(values.rating());
        String content = validContent(values.content());
        Review review = new Review();
        review.orderItemId = values.orderItemId();
        review.productId = values.productId();
        review.memberId = values.memberId();
        review.nickname = values.nickname();
        review.rating = (short) rating;
        review.content = content;
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
     * 작성자가 별점 · 본문을 고친다. 규칙 위반이면 값을 바꾸지 않는다.
     */
    public void update(Long memberId, Integer rating, String content) {
        checkAuthor(memberId);
        int newRating = validRating(rating);
        String newContent = validContent(content);
        this.rating = (short) newRating;
        this.content = newContent;
    }

    public int getRating() {
        return rating;
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

    private static String validContent(String content) {
        String trimmed = content == null ? "" : content.strip();
        if (trimmed.isEmpty() || trimmed.length() > MAX_CONTENT_LENGTH) {
            throw new ReviewRuleException(ReviewRule.CONTENT);
        }
        return trimmed;
    }
}
