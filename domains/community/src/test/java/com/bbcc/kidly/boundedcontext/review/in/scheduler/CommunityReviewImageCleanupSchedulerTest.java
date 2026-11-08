package com.bbcc.kidly.boundedcontext.review.in.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.bbcc.kidly.boundedcontext.review.app.ReviewImageCleanupService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * 사진 정리 스케줄러는 기본 꺼짐이다: kidly.community.review.image.cleanup.enabled가 true일 때만 뜬다(값이 없거나 false면 없다).
 */
class CommunityReviewImageCleanupSchedulerTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withUserConfiguration(CommunityReviewImageCleanupScheduler.class)
        .withBean(ReviewImageCleanupService.class, () -> mock(ReviewImageCleanupService.class))
        .withPropertyValues("kidly.community.review.image.cleanup.interval=PT10M");

    @ParameterizedTest(name = "enabled={0} → 스케줄러 {1}")
    @CsvSource({"'', false", "false, false", "true, true"})
    @DisplayName("값이 없거나 false면 스케줄러가 없고, true일 때만 뜬다")
    void onlyWhenEnabled(String enabled, boolean expected) {
        ApplicationContextRunner configured = enabled.isEmpty() ? runner
            : runner.withPropertyValues("kidly.community.review.image.cleanup.enabled=" + enabled);

        configured.run(context -> assertThat(context.containsBean("communityReviewImageCleanupScheduler"))
            .isEqualTo(expected));
    }
}
