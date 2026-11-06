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

    @Test
    @DisplayName("일부 수정: 별점만 보내면 별점만, 본문만 보내면 본문만 바뀐다")
    void partialUpdate() {
        Review review = review(5, "좋아요");

        review.update(AUTHOR, Review.Change.ratingOnly(2));
        assertThat(review.getRating()).isEqualTo(2);
        assertThat(review.getContent()).isEqualTo("좋아요");

        review.update(AUTHOR, Review.Change.contentOnly(" 바뀐 본문 "));
        assertThat(review.getRating()).isEqualTo(2);
        assertThat(review.getContent()).isEqualTo("바뀐 본문");
    }

    @Test
    @DisplayName("바꿀 값이 하나도 없으면 변경 없음 규칙 위반, 보낸 값이 null이면 그 값의 규칙 위반")
    void noChangesOrNullValues() {
        Review review = review(5, "좋아요");

        assertThatThrownBy(() -> review.update(AUTHOR, new Review.Change(false, null, false, null)))
            .isInstanceOfSatisfying(ReviewRuleException.class,
                e -> assertThat(e.rule()).isEqualTo(ReviewRule.NO_CHANGES));
        assertThatThrownBy(() -> review.update(AUTHOR, Review.Change.ratingOnly(null)))
            .isInstanceOfSatisfying(ReviewRuleException.class, e -> assertThat(e.rule()).isEqualTo(ReviewRule.RATING));
        assertThatThrownBy(() -> review.update(AUTHOR, Review.Change.contentOnly(null)))
            .isInstanceOfSatisfying(ReviewRuleException.class,
                e -> assertThat(e.rule()).isEqualTo(ReviewRule.CONTENT));
        assertThat(review.getRating()).isEqualTo(5);
        assertThat(review.getContent()).isEqualTo("좋아요");
    }

    @Test
    @DisplayName("핏은 세 값 또는 없음(null)이고, 사이즈가 있는 주문상품만 핏을 받는다")
    void fitOnCreate() {
        assertThat(withFit(Fit.SMALL, true).getFit()).isEqualTo(Fit.SMALL);
        assertThat(withFit(null, false).getFit()).isNull();
        assertThatThrownBy(() -> withFit(Fit.LARGE, false))
            .isInstanceOfSatisfying(ReviewRuleException.class,
                e -> assertThat(e.rule()).isEqualTo(ReviewRule.FIT_NOT_APPLICABLE));
    }

    @Test
    @DisplayName("수정에서 핏만 바꾸거나 지울 수 있고(별점 · 본문 그대로), 사이즈 없는 주문상품에 핏을 넣으면 규칙 위반")
    void fitOnUpdate() {
        Review review = withFit(Fit.SMALL, true);

        review.update(AUTHOR, Review.Change.fitOnly(Fit.LARGE), true);
        assertThat(review.getFit()).isEqualTo(Fit.LARGE);
        assertThat(review.getRating()).isEqualTo(5);

        review.update(AUTHOR, Review.Change.fitOnly(null), true);
        assertThat(review.getFit()).isNull();

        assertThatThrownBy(() -> review.update(AUTHOR, Review.Change.fitOnly(Fit.SMALL), false))
            .isInstanceOfSatisfying(ReviewRuleException.class,
                e -> assertThat(e.rule()).isEqualTo(ReviewRule.FIT_NOT_APPLICABLE));
        assertThat(review.getFit()).isNull();
        // 사이즈가 없어도 핏을 지우는 것은 된다
        review.update(AUTHOR, Review.Change.fitOnly(null), false);
    }

    @Test
    @DisplayName("사진만 바꾸는 수정도 바꿀 값이 있는 것으로 보고 다른 값은 그대로 둔다. 남이 고치면 AUTHOR")
    void imagesOnlyUpdate() {
        Review review = withFit(Fit.SMALL, true);

        review.update(AUTHOR, Review.Change.imagesOnly(), true);

        assertThat(review.getRating()).isEqualTo(5);
        assertThat(review.getContent()).isEqualTo("좋아요");
        assertThat(review.getFit()).isEqualTo(Fit.SMALL);
        assertThatThrownBy(() -> review.update(AUTHOR + 1, Review.Change.imagesOnly(), true))
            .isInstanceOfSatisfying(ReviewRuleException.class,
                e -> assertThat(e.rule()).isEqualTo(ReviewRule.AUTHOR));
        assertThatThrownBy(() -> review.update(AUTHOR, new Review.Change(false, null, false, null, false, null, false),
            true))
            .isInstanceOfSatisfying(ReviewRuleException.class,
                e -> assertThat(e.rule()).isEqualTo(ReviewRule.NO_CHANGES));
    }

    @Test
    @DisplayName("수정 미리 확인(checkUpdate)은 수정과 같은 규칙으로 막고 값을 바꾸지 않는다")
    void checkUpdateDoesNotChange() {
        Review review = withFit(Fit.SMALL, true);

        review.checkUpdate(AUTHOR, new Review.Change(true, 1, true, "바뀐 글", true, Fit.LARGE, true), true);
        assertThatThrownBy(() -> review.checkUpdate(AUTHOR, Review.Change.ratingOnly(9), true))
            .isInstanceOfSatisfying(ReviewRuleException.class, e -> assertThat(e.rule()).isEqualTo(ReviewRule.RATING));
        assertThatThrownBy(() -> review.checkUpdate(AUTHOR + 1, Review.Change.ratingOnly(1), true))
            .isInstanceOfSatisfying(ReviewRuleException.class, e -> assertThat(e.rule()).isEqualTo(ReviewRule.AUTHOR));
        assertThatThrownBy(() -> review.checkUpdate(AUTHOR, Review.Change.fitOnly(Fit.LARGE), false))
            .isInstanceOfSatisfying(ReviewRuleException.class,
                e -> assertThat(e.rule()).isEqualTo(ReviewRule.FIT_NOT_APPLICABLE));

        assertThat(review.getRating()).isEqualTo(5);
        assertThat(review.getContent()).isEqualTo("좋아요");
        assertThat(review.getFit()).isEqualTo(Fit.SMALL);
        assertThat(review.getContentVersion()).isZero();
    }

    private static Review withFit(Fit fit, boolean sizeApplicable) {
        return Review.create(new Review.Values(11L, 14L, AUTHOR, "닉네임", 5, "좋아요", fit, sizeApplicable));
    }

    private static Review review(Integer rating, String content) {
        return Review.create(new Review.Values(11L, 14L, AUTHOR, "닉네임", rating, content));
    }
}
