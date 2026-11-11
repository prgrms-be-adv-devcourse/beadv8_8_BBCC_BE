package com.bbcc.kidly.boundedcontext.review.out.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.review.app.ReviewImageCleanupProperties;
import com.bbcc.kidly.boundedcontext.review.app.ReviewImageProperties;
import com.bbcc.kidly.boundedcontext.review.app.port.ReviewImageCleanupStorage;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/**
 * 리뷰 사진 정리 설정(kidly.community.review.image.cleanup.*): 지우기 · 목록 방식은 upload-check와 같은 이름으로 고르고, 저장소에 닿지
 * 않는 설정(none)이면 정리가 돌지 않는다. 값이 틀리면 키 이름이 나오는 오류로 기동에 실패하고, 로컬 · 테스트 프로필은 정리를 끈다.
 */
@ExtendWith(OutputCaptureExtension.class)
class CommunityReviewImageCleanupConfigTest {

    private static final String IMAGE = "kidly.community.review.image.";
    private static final String CLEANUP = IMAGE + "cleanup.";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withUserConfiguration(CommunityReviewImageStorageConfig.class, AppProperties.class)
        .withPropertyValues(
            IMAGE + "bucket=team04-kidly-s3",
            IMAGE + "region=ap-northeast-2",
            IMAGE + "key-prefix=dev",
            IMAGE + "fake-credentials=true",
            IMAGE + "upload-url-expiry=PT5M",
            IMAGE + "max-count=5",
            IMAGE + "max-file-size=10MB",
            CLEANUP + "enabled=true",
            CLEANUP + "interval=PT10M",
            CLEANUP + "batch-size=100",
            CLEANUP + "pending-delay=PT1H",
            CLEANUP + "max-backoff=PT1H",
            CLEANUP + "orphan-grace=P1D",
            CLEANUP + "scan-page-size=500",
            CLEANUP + "scan-delete-limit=100");

    @Test
    @DisplayName("upload-check s3(기본)면 S3 지우기 · 목록으로 정리하고, 값을 그대로 읽는다")
    void s3Store() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            ReviewImageCleanupStorage storage = context.getBean(ReviewImageCleanupStorage.class);
            assertThat(storage.available()).isTrue();
            assertThat(context).hasBean("communityS3ReviewImageObjectStore");
            assertThat(storage.keyOf("https://team04-kidly-s3.s3.ap-northeast-2.amazonaws.com/dev/reviews/13/"
                + "0b6f3c1e-8a2d-4c5b-9e7f-1a2b3c4d5e6f.jpg"))
                .contains("dev/reviews/13/0b6f3c1e-8a2d-4c5b-9e7f-1a2b3c4d5e6f.jpg");
            ReviewImageCleanupProperties properties = context.getBean(ReviewImageCleanupProperties.class);
            assertThat(properties.orphanGrace()).isEqualTo(Duration.ofDays(1));
            assertThat(properties.scanDeleteLimit()).isEqualTo(100);
        });
    }

    @Test
    @DisplayName("upload-check none(S3에 닿지 않음)이면 정리를 켜도 돌지 않고 한 줄 남긴다")
    void noneStoreNotAvailable(CapturedOutput output) {
        runner.withPropertyValues(IMAGE + "upload-check=none").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(ReviewImageCleanupStorage.class).available()).isFalse();
            assertThat(context).doesNotHaveBean("communityS3ReviewImageObjectStore");
        });
        assertThat(output.getAll()).contains("리뷰 사진 정리 꺼짐(upload-check: none)");
    }

    @Test
    @DisplayName("정리를 끈 설정이면 none이어도 꺼짐 줄을 남기지 않는다")
    void noLogWhenDisabled(CapturedOutput output) {
        runner.withPropertyValues(IMAGE + "upload-check=none", CLEANUP + "enabled=false")
            .run(context -> assertThat(context).hasNotFailed());
        assertThat(output.getAll()).doesNotContain("리뷰 사진 정리 꺼짐");
    }

    @Test
    @DisplayName("enabled 값이 없으면 정리는 꺼짐(false)이다")
    void disabledByDefault() {
        new ApplicationContextRunner()
            .withUserConfiguration(AppProperties.class)
            .withPropertyValues(IMAGE + "max-count=5", IMAGE + "max-file-size=10MB", CLEANUP + "interval=PT10M",
                CLEANUP + "batch-size=100", CLEANUP + "pending-delay=PT1H", CLEANUP + "max-backoff=PT1H",
                CLEANUP + "orphan-grace=P1D", CLEANUP + "scan-page-size=500", CLEANUP + "scan-delete-limit=100")
            .run(context -> assertThat(context.getBean(ReviewImageCleanupProperties.class).enabled()).isFalse());
    }

    @ParameterizedTest(name = "[{0}]")
    @ValueSource(strings = {"PT20M", "PT30M"})
    @DisplayName("훑기 유예(orphan-grace)가 업로드 주소 만료(PT30M)보다 길지 않으면 두 키 이름이 나오는 오류로 기동에 실패한다")
    void orphanGraceMustExceedUploadUrlExpiry(String orphanGrace) {
        runner.withPropertyValues(IMAGE + "upload-url-expiry=PT30M", CLEANUP + "orphan-grace=" + orphanGrace)
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).hasStackTraceContaining(CLEANUP + "orphan-grace")
                    .hasStackTraceContaining(IMAGE + "upload-url-expiry");
            });
        runner.withPropertyValues(IMAGE + "upload-url-expiry=PT30M", CLEANUP + "orphan-grace=PT31M")
            .run(context -> assertThat(context).hasNotFailed());
    }

    @ParameterizedTest(name = "[{0}]")
    @ValueSource(strings = {"interval=PT0S", "batch-size=0", "batch-size=1001", "pending-delay=-PT1S",
        "pending-delay=PT0S", "pending-delay=PT4M59S",
        "max-backoff=PT30S", "orphan-grace=PT5M", "scan-page-size=0", "scan-page-size=1001", "scan-delete-limit=0",
        "interval=", "orphan-grace="})
    @DisplayName("정리 설정 값이 틀리면 그 키가 나오는 오류로 기동에 실패한다")
    void failsOnInvalidValue(String setting) {
        runner.withPropertyValues(CLEANUP + setting).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("kidly.community.review.image.cleanup");
        });
    }

    @Test
    @DisplayName("community.yml: 기본은 꺼짐(환경변수로 켠다), 로컬 · 테스트 프로필은 꺼짐")
    void profiles() throws IOException {
        List<PropertySource<?>> documents = new YamlPropertySourceLoader()
            .load("community.yml", new ClassPathResource("community.yml"));

        assertThat(documents.getFirst().getProperty(CLEANUP + "enabled"))
            .hasToString("${COMMUNITY_REVIEW_IMAGE_CLEANUP_ENABLED:false}");
        assertThat(documents).anySatisfy(document -> {
            assertThat(document.getProperty("spring.config.activate.on-profile")).hasToString("local | test");
            assertThat(document.getProperty(CLEANUP + "enabled")).hasToString("false");
        });
    }

    @Configuration
    @EnableConfigurationProperties({ReviewImageProperties.class, ReviewImageCleanupProperties.class})
    static class AppProperties {
    }
}
