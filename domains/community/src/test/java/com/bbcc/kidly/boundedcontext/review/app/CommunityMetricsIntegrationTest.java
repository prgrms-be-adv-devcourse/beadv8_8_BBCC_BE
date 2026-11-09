package com.bbcc.kidly.boundedcontext.review.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.review.app.ReviewService.ImageUrlsInput;
import com.bbcc.kidly.boundedcontext.review.app.port.CommunityDeadLetters.Received;
import com.bbcc.kidly.boundedcontext.review.app.port.ContentModerator.FailureCause;
import com.bbcc.kidly.boundedcontext.review.app.port.ContentModerator.Result;
import com.bbcc.kidly.boundedcontext.review.app.port.ReviewImageStorage;
import com.bbcc.kidly.boundedcontext.review.domain.ModerationReason;
import com.bbcc.kidly.boundedcontext.review.out.moderation.FakeModerationAdapter;
import com.bbcc.kidly.boundedcontext.review.out.storage.FakeReviewImageUploadChecker;
import com.bbcc.kidly.boundedcontext.support.IntegrationTest;
import com.bbcc.kidly.global.error.BusinessException;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 운영 지표를 실제 DB로 확인한다: 게이지는 DLT 보관 · 검사 대기를 만들면 바뀌고, 카운터는 그 경로에서만 는다.
 * 테스트 프로필은 두 스케줄러를 끄므로 스케줄러 시계열이 없다.
 */
@IntegrationTest
class CommunityMetricsIntegrationTest {

    private static final long ME = 13L;

    @Autowired
    private MeterRegistry registry;

    @Autowired
    private CommunityMetrics metrics;

    @Autowired
    private CommunityDeadLetterService deadLetterService;

    @Autowired
    private ReviewModerationService moderationService;

    @Autowired
    private ReviewService reviewService;

    @Autowired
    private ReviewImageStorage imageStorage;

    @Autowired
    private FakeModerationAdapter fake;

    @Autowired
    private FakeReviewImageUploadChecker uploadChecker;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void reset() {
        fake.reset();
        uploadChecker.reset();
        metrics.clearModerationPendingCache();
    }

    @Test
    @DisplayName("DLT를 보관하면 미처리 수가 늘고 가장 오래된 나이가 0 이상이다. 같은 위치를 다시 보관해도 수는 그대로다")
    void deadLetterGauges() {
        double before = gauge(CommunityMetrics.DLT_MESSAGES, "state", "open");

        deadLetterService.receive(received(7));
        deadLetterService.receive(received(7));
        deadLetterService.receive(received(8));

        assertThat(gauge(CommunityMetrics.DLT_MESSAGES, "state", "open")).isEqualTo(before + 2);
        assertThat(gauge(CommunityMetrics.DLT_OLDEST_AGE)).isGreaterThanOrEqualTo(0.0).isLessThan(60.0);
    }

    @Test
    @DisplayName("검사 대기를 만들면 waiting(시도 0) · retrying(시도 1 ~ 상한 전) · exhausted(상한 3 도달)가 바뀐다")
    void moderationPendingGauges() {
        pendingReview(101, 0);
        pendingReview(102, 0);
        pendingReview(103, 1);
        pendingReview(104, 2);
        pendingReview(105, 3);
        jdbcTemplate.update("insert into community.review (order_item_id, product_id, member_id, nickname, rating, "
            + "content, created_at, updated_at, moderation_status) values (106, 14, 13, '닉', 4, '본문', now(), now(), "
            + "'PASSED')");

        assertThat(gauge(CommunityMetrics.MODERATION_PENDING, "stage", "waiting")).isEqualTo(2.0);
        assertThat(gauge(CommunityMetrics.MODERATION_PENDING, "stage", "retrying")).isEqualTo(2.0);
        assertThat(gauge(CommunityMetrics.MODERATION_PENDING, "stage", "exhausted")).isEqualTo(1.0);
        assertThat(gauge(CommunityMetrics.MODERATION_PENDING_OLDEST_AGE)).isGreaterThanOrEqualTo(0.0);
    }

    @Test
    @DisplayName("검사기 호출 결과 카운터는 검사 처리에서 호출마다 는다(통과 · 걸림 · 판단 못 함)")
    void moderationChecksCount() {
        pendingReview(201, 0);
        pendingReview(202, 0);
        pendingReview(203, 0);
        // 걸린 리뷰는 숨기며 집계에서 뺀다(집계 행이 있어야 한다)
        jdbcTemplate.update("insert into community.review_stats (product_id, review_count, rating_sum, created_at, "
            + "updated_at) values (14, 3, 12, now(), now())");
        double passed = counter(CommunityMetrics.MODERATION_CHECKS, "result", "passed", "cause", "none");
        double flagged = counter(CommunityMetrics.MODERATION_CHECKS, "result", "flagged", "cause", "none");
        double timeout = counter(CommunityMetrics.MODERATION_CHECKS, "result", "undecided", "cause", "timeout");
        fake.willReturn(Result.passed(), Result.flagged(ModerationReason.HATE),
            Result.undecided(FailureCause.TIMEOUT));

        moderationService.processPending();

        assertThat(counter(CommunityMetrics.MODERATION_CHECKS, "result", "passed", "cause", "none"))
            .isEqualTo(passed + 1);
        assertThat(counter(CommunityMetrics.MODERATION_CHECKS, "result", "flagged", "cause", "none"))
            .isEqualTo(flagged + 1);
        assertThat(counter(CommunityMetrics.MODERATION_CHECKS, "result", "undecided", "cause", "timeout"))
            .isEqualTo(timeout + 1);
    }

    @Test
    @DisplayName("사진 확인 불가 카운터는 503(IMAGE_STORAGE_UNAVAILABLE) 경로에서만 는다(올리지 않음 400 · 성공은 그대로)")
    void storageUnavailableOnlyOn503() {
        jdbcTemplate.update("insert into community.review_eligibility (order_item_id, order_id, member_id, product_id, "
            + "option_id, size, confirmed_at, created_at, updated_at) values (11, 12, ?, 14, 15, null, now(), now(), "
            + "now())", ME);
        double before = counter(CommunityMetrics.IMAGE_STORAGE_UNAVAILABLE, "operation", "upload_check");
        String missing = url(0);
        String unavailable = url(1);
        uploadChecker.markUnavailable(unavailable);

        assertError(() -> write(List.of(missing)), ReviewErrorCode.IMAGE_NOT_UPLOADED);
        assertThat(counter(CommunityMetrics.IMAGE_STORAGE_UNAVAILABLE, "operation", "upload_check")).isEqualTo(before);

        assertError(() -> write(List.of(unavailable)), ReviewErrorCode.IMAGE_STORAGE_UNAVAILABLE);
        assertThat(counter(CommunityMetrics.IMAGE_STORAGE_UNAVAILABLE, "operation", "upload_check"))
            .isEqualTo(before + 1);

        uploadChecker.markUploaded(missing);
        write(List.of(missing));
        assertThat(counter(CommunityMetrics.IMAGE_STORAGE_UNAVAILABLE, "operation", "upload_check"))
            .isEqualTo(before + 1);
    }

    @Test
    @DisplayName("테스트 프로필은 두 스케줄러를 끄므로 스케줄러 마지막 성공 시계열이 없다")
    void noSchedulerSeriesWhenDisabled() {
        assertThat(registry.find(CommunityMetrics.SCHEDULER_LAST_SUCCESS).gauges()).isEmpty();
    }

    private void write(List<String> urls) {
        reviewService.write(ME, "닉", "11", 4, "좋아요", ReviewService.FitInput.absent(), ImageUrlsInput.of(urls));
    }

    private String url(int index) {
        return imageStorage.ownImageUrlPrefix(ME) + String.format("0b6f3c1e-8a2d-4c5b-9e7f-%012d", index) + ".jpg";
    }

    private void pendingReview(long orderItemId, int attempts) {
        jdbcTemplate.update("insert into community.review (order_item_id, product_id, member_id, nickname, rating, "
            + "content, created_at, updated_at, moderation_status, moderation_attempts) "
            + "values (?, 14, 13, '닉', 4, '본문', now(), now(), 'PENDING', ?)", orderItemId, attempts);
    }

    private static Received received(long offset) {
        return new Received("kidly.order.events.community.dlt", 0, offset, "kidly.order.events", 1, offset, "1",
            "order-item.purchase-confirmed", "0b6f3c1e-8a2d-4c5b-9e7f-1a2b3c4d5e6f", "{}", "IllegalStateException");
    }

    private double gauge(String name, String... tags) {
        return registry.get(name).tags(tags).gauge().value();
    }

    private double counter(String name, String... tags) {
        return registry.get(name).tags(tags).counter().count();
    }

    private static void assertError(Runnable call, ReviewErrorCode code) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(code));
    }
}
