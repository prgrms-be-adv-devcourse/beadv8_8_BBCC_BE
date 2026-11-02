package com.bbcc.kidly.boundedcontext.review.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class ReviewTest {

    private static final long AUTHOR = 13L;

    @ParameterizedTest
    @ValueSource(ints = {1, 5})
    @DisplayName("별점 1 · 5(경계)는 받는다")
    void ratingBoundsAccepted(int rating) {
        assertThat(review(rating, "좋아요").getRating()).isEqualTo(rating);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 6, -1})
    @NullSource
    @DisplayName("별점이 1~5가 아니거나 없으면 별점 규칙 위반")
    void invalidRating(Integer rating) {
        assertThatThrownBy(() -> review(rating, "좋아요"))
            .isInstanceOfSatisfying(ReviewRuleException.class, e -> assertThat(e.rule()).isEqualTo(ReviewRule.RATING));
    }

    @Test
    @DisplayName("본문 1자 · 1000자(경계)는 받고, 앞뒤 공백은 잘라 저장한다")
    void contentBoundsAccepted() {
        assertThat(review(5, "a").getContent()).isEqualTo("a");
        assertThat(review(5, "가".repeat(1000)).getContent()).hasSize(1000);
        assertThat(review(5, "  좋아요 \n").getContent()).isEqualTo("좋아요");
        assertThat(review(5, " " + "가".repeat(1000) + " ").getContent()).hasSize(1000);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\n\t"})
    @NullSource
    @DisplayName("본문이 없거나 공백뿐이면 본문 규칙 위반")
    void blankContent(String content) {
        assertThatThrownBy(() -> review(5, content))
            .isInstanceOfSatisfying(ReviewRuleException.class, e -> assertThat(e.rule()).isEqualTo(ReviewRule.CONTENT));
    }

    @Test
    @DisplayName("본문이 1001자면 본문 규칙 위반")
    void tooLongContent() {
        assertThatThrownBy(() -> review(5, "가".repeat(1001)))
            .isInstanceOfSatisfying(ReviewRuleException.class, e -> assertThat(e.rule()).isEqualTo(ReviewRule.CONTENT));
    }

    @Test
    @DisplayName("작성 값(주문상품 · 상품 · 작성자 · 작성 시점 닉네임)을 저장한다")
    void create() {
        Review review = Review.create(new Review.Values(11L, 14L, AUTHOR, "닉네임", 4, "좋아요"));

        assertThat(review.getOrderItemId()).isEqualTo(11L);
        assertThat(review.getProductId()).isEqualTo(14L);
        assertThat(review.getMemberId()).isEqualTo(AUTHOR);
        assertThat(review.getNickname()).isEqualTo("닉네임");
    }

    @Test
    @DisplayName("작성자는 별점 · 본문을 고칠 수 있다")
    void authorUpdates() {
        Review review = review(5, "좋아요");

        review.update(AUTHOR, 2, " 별로예요 ");

        assertThat(review.getRating()).isEqualTo(2);
        assertThat(review.getContent()).isEqualTo("별로예요");
    }

    @Test
    @DisplayName("작성자가 아니면 수정 · 삭제 확인에서 작성자 규칙 위반이고 값은 그대로다")
    void notAuthor() {
        Review review = review(5, "좋아요");

        assertThatThrownBy(() -> review.update(AUTHOR + 1, 1, "남의 수정"))
            .isInstanceOfSatisfying(ReviewRuleException.class, e -> assertThat(e.rule()).isEqualTo(ReviewRule.AUTHOR));
        assertThatThrownBy(() -> review.checkAuthor(AUTHOR + 1))
            .isInstanceOfSatisfying(ReviewRuleException.class, e -> assertThat(e.rule()).isEqualTo(ReviewRule.AUTHOR));
        assertThat(review.getRating()).isEqualTo(5);
        assertThat(review.getContent()).isEqualTo("좋아요");
    }

    @Test
    @DisplayName("작성자가 고치려는 값이 규칙 위반이면 값은 그대로다")
    void invalidUpdateKeepsValues() {
        Review review = review(5, "좋아요");

        assertThatThrownBy(() -> review.update(AUTHOR, 6, "고침")).isInstanceOf(ReviewRuleException.class);
        assertThat(review.getRating()).isEqualTo(5);
        assertThat(review.getContent()).isEqualTo("좋아요");
    }

    private static Review review(Integer rating, String content) {
        return Review.create(new Review.Values(11L, 14L, AUTHOR, "닉네임", rating, content));
    }
}
