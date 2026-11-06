package com.bbcc.kidly.boundedcontext.review.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 리뷰 검사 상태: 저장하면 검사 대기(공개), 검사 결과 · 관리자 조치 · 글 · 사진 수정에 따른 상태와 공개 여부.
 */
class ReviewModerationTest {

    private static final long AUTHOR = 13L;
    private static final long ADMIN = 99L;
    // 나노초가 있는 고정 시각(저장은 마이크로초)
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 6, 10, 0, 0, 123_456_789);

    @Test
    @DisplayName("새 리뷰는 검사 대기(PENDING)이고 공개다. 시도 0 · 버전 0")
    void newReviewIsPendingAndPublic() {
        Review review = review();

        assertThat(review.getModerationStatus()).isEqualTo(ModerationStatus.PENDING);
        assertThat(review.isPublic()).isTrue();
        assertThat(review.getModerationAttempts()).isZero();
        assertThat(review.getContentVersion()).isZero();
    }

    @Test
    @DisplayName("공개 여부: PENDING · PASSED · RESTORED는 공개, HIDDEN · CONFIRMED_HIDDEN은 숨김")
    void publicStatuses() {
        assertThat(ModerationStatus.PENDING.isPublic()).isTrue();
        assertThat(ModerationStatus.PASSED.isPublic()).isTrue();
        assertThat(ModerationStatus.RESTORED.isPublic()).isTrue();
        assertThat(ModerationStatus.HIDDEN.isPublic()).isFalse();
        assertThat(ModerationStatus.CONFIRMED_HIDDEN.isPublic()).isFalse();
    }

    @Test
    @DisplayName("검사 결과는 읽을 때의 버전 · 시도 횟수가 그대로이고 아직 대기일 때만 받는다")
    void awaitingOnlyWhenUnchanged() {
        Review review = review();

        assertThat(review.isAwaitingModeration(0L, 0)).isTrue();
        assertThat(review.isAwaitingModeration(1L, 0)).isFalse();
        assertThat(review.isAwaitingModeration(0L, 1)).isFalse();
        review.pass(NOW);
        assertThat(review.isAwaitingModeration(0L, 0)).isFalse();
    }

    @Test
    @DisplayName("검사 통과 · 걸림 · 판단 못 함: 통과는 PASSED, 걸림은 HIDDEN + 이유, 판단 못 함은 대기 그대로 + 시도 +1")
    void moderationOutcomes() {
        Review passed = review();
        passed.pass(NOW);
        assertThat(passed.getModerationStatus()).isEqualTo(ModerationStatus.PASSED);
        assertThat(passed.getModeratedAt()).isEqualTo(ReviewTimes.toStoredPrecision(NOW));

        Review hidden = review();
        hidden.hideByModeration(ModerationReason.HATE, NOW);
        assertThat(hidden.getModerationStatus()).isEqualTo(ModerationStatus.HIDDEN);
        assertThat(hidden.getModerationReason()).isEqualTo(ModerationReason.HATE);
        assertThat(hidden.isPublic()).isFalse();

        Review undecided = review();
        undecided.recordUndecided();
        assertThat(undecided.getModerationStatus()).isEqualTo(ModerationStatus.PENDING);
        assertThat(undecided.getModerationAttempts()).isEqualTo(1);
    }

    @ParameterizedTest(name = "[{0}]")
    @EnumSource(value = ModerationStatus.class, names = {"PENDING", "PASSED", "RESTORED"})
    @DisplayName("공개 상태에서 글 · 사진이 바뀌면 다시 검사 대기(PENDING) · 버전 +1 · 시도 0 · 이유 지움")
    void contentChangeRequeues(ModerationStatus status) {
        Review review = withStatus(status);
        ReflectionTestUtils.setField(review, "moderationAttempts", 3);

        review.markContentChanged();

        assertThat(review.getModerationStatus()).isEqualTo(ModerationStatus.PENDING);
        assertThat(review.getContentVersion()).isEqualTo(1L);
        assertThat(review.getModerationAttempts()).isZero();
        assertThat(review.getModerationReason()).isNull();
        assertThat(review.isPublic()).isTrue();
    }

    @ParameterizedTest(name = "[{0}]")
    @EnumSource(value = ModerationStatus.class, names = {"HIDDEN", "CONFIRMED_HIDDEN"})
    @DisplayName("숨김 상태에서 글 · 사진이 바뀌면 숨김 그대로(관리자 판단), 버전만 +1")
    void contentChangeKeepsHidden(ModerationStatus status) {
        Review review = withStatus(status);

        review.markContentChanged();

        assertThat(review.getModerationStatus()).isEqualTo(status);
        assertThat(review.getContentVersion()).isEqualTo(1L);
        assertThat(review.isPublic()).isFalse();
    }

    @Test
    @DisplayName("관리자 숨김 확정: 공개였으면 true(집계에서 뺀다), 이미 숨김이면 false, 다시 보내도 같은 상태")
    void adminHide() {
        Review open = withStatus(ModerationStatus.PASSED);
        assertThat(open.hideByAdmin(ADMIN, NOW)).isTrue();
        assertThat(open.getModerationStatus()).isEqualTo(ModerationStatus.CONFIRMED_HIDDEN);
        assertThat(open.getModeratedByAdminId()).isEqualTo(ADMIN);
        assertThat(open.hideByAdmin(ADMIN, NOW)).isFalse();
        assertThat(open.getModerationStatus()).isEqualTo(ModerationStatus.CONFIRMED_HIDDEN);

        Review hidden = withStatus(ModerationStatus.HIDDEN);
        assertThat(hidden.hideByAdmin(ADMIN, NOW)).isFalse();
        assertThat(hidden.getModerationStatus()).isEqualTo(ModerationStatus.CONFIRMED_HIDDEN);
    }

    @Test
    @DisplayName("관리자 다시 공개: 숨김이었으면 true(집계에 더한다), 이미 공개면 false, 다시 보내도 같은 상태")
    void adminRestore() {
        Review hidden = withStatus(ModerationStatus.CONFIRMED_HIDDEN);
        assertThat(hidden.restoreByAdmin(ADMIN, NOW)).isTrue();
        assertThat(hidden.getModerationStatus()).isEqualTo(ModerationStatus.RESTORED);
        assertThat(hidden.isPublic()).isTrue();
        assertThat(hidden.restoreByAdmin(ADMIN, NOW)).isFalse();

        Review pending = withStatus(ModerationStatus.PENDING);
        assertThat(pending.restoreByAdmin(ADMIN, NOW)).isFalse();
        assertThat(pending.getModerationStatus()).isEqualTo(ModerationStatus.RESTORED);
    }

    private static Review withStatus(ModerationStatus status) {
        Review review = review();
        ReflectionTestUtils.setField(review, "moderationStatus", status);
        return review;
    }

    private static Review review() {
        return Review.create(new Review.Values(11L, 14L, AUTHOR, "닉네임", 5, "좋아요"));
    }
}
