package com.bbcc.kidly.boundedcontext.review.out.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.review.app.ReviewImageProperties;
import com.bbcc.kidly.boundedcontext.review.app.port.ReviewImageStorage;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.context.properties.bind.validation.ValidationErrors;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.s3.S3Client;
import org.springframework.validation.FieldError;

/**
 * 리뷰 사진 설정(kidly.community.review.image.*)을 시작 때 검사한다. 값이 비거나 형식이 틀리면 키 이름이 나오는 오류로 기동에 실패한다.
 */
@ExtendWith(OutputCaptureExtension.class)
class CommunityReviewImageStorageConfigTest {

    private static final String PREFIX = "kidly.community.review.image.";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withUserConfiguration(CommunityReviewImageStorageConfig.class, AppProperties.class)
        .withPropertyValues(
            PREFIX + "bucket=team04-kidly-s3",
            PREFIX + "region=ap-northeast-2",
            PREFIX + "key-prefix=local",
            PREFIX + "fake-credentials=true",
            PREFIX + "upload-url-expiry=PT5M",
            PREFIX + "max-count=5",
            PREFIX + "max-file-size=10MB");

    @Test
    @DisplayName("설정이 맞으면 가짜 자격 증명으로 리뷰 사진 저장소를 만들고 값을 그대로 읽는다")
    void createsStorage() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(ReviewImageStorage.class);
            assertThat(context).hasBean("communityReviewImageS3Presigner");
            assertThat(context.getBean(ReviewImageProperties.class).maxCount()).isEqualTo(5);
            assertThat(context.getBean(ReviewImageStorage.class).ownImageUrlPrefix(13L))
                .isEqualTo("https://team04-kidly-s3.s3.ap-northeast-2.amazonaws.com/local/reviews/13/");
        });
    }

    @Test
    @DisplayName("업로드 확인 방식 기본은 s3(동기 S3 클라이언트로 HeadObject), none이면 S3 클라이언트를 만들지 않고 한 줄 남긴다")
    void uploadCheckSelection(CapturedOutput output) {
        runner.run(context -> {
            assertThat(context).hasSingleBean(S3Client.class);
            assertThat(context).hasBean("communityS3ReviewImageUploadChecker");
        });
        runner.withPropertyValues(PREFIX + "upload-check=none").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(S3Client.class);
        });
        assertThat(output.getAll()).contains("리뷰 사진 업로드 확인 꺼짐(upload-check: none)");
    }

    @Test
    @DisplayName("모르는 업로드 확인 방식이면 그 키 이름이 나오는 오류로 기동에 실패한다")
    void failsOnUnknownUploadCheck() {
        runner.withPropertyValues(PREFIX + "upload-check=gcs").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining(PREFIX + "upload-check");
        });
    }

    @ParameterizedTest(name = "[{0}]")
    @ValueSource(strings = {"key-prefix=", "key-prefix=dev/", "key-prefix=/dev", "key-prefix=Dev", "key-prefix=..",
        "key-prefix=dev,prod", "bucket=", "region=", "upload-url-expiry=PT0S", "max-count=0", "max-count=101",
        "max-file-size=0B", "upload-check=", "upload-check-timeout=PT0S"})
    @DisplayName("값이 비었거나 형식이 틀리면 그 키(전체 이름)가 나오는 오류로 기동에 실패한다")
    void failsOnInvalidValue(String setting) {
        runner.withPropertyValues(PREFIX + setting)
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).rootCause().isNotNull();
                assertThat(context.getStartupFailure()).hasStackTraceContaining("kidly.community.review.image");
                // 오류는 키를 자바 이름(key-prefix → keyPrefix)으로 담는다. @AssertTrue 검사는 그 이름 뒤에 붙는다(maxFileSizePositive)
                assertThat(failedKeys(context.getStartupFailure())).anySatisfy(
                    key -> assertThat(key).startsWith(PREFIX + camelCase(setting.substring(0, setting.indexOf('=')))));
            });
    }

    private static String camelCase(String kebab) {
        StringBuilder name = new StringBuilder();
        boolean upper = false;
        for (char c : kebab.toCharArray()) {
            if (c == '-') {
                upper = true;
            } else {
                name.append(upper ? Character.toUpperCase(c) : c);
                upper = false;
            }
        }
        return name.toString();
    }

    // 검사에 걸린 설정 키 전체 이름(예: kidly.community.review.image.keyPrefix)
    private static List<String> failedKeys(Throwable failure) {
        Throwable cause = failure;
        while (cause != null && !(cause instanceof BindValidationException)) {
            cause = cause.getCause();
        }
        assertThat(cause).as("설정 검사 실패").isInstanceOf(BindValidationException.class);
        ValidationErrors errors = ((BindValidationException) cause).getValidationErrors();
        return errors.getAllErrors().stream()
            .filter(FieldError.class::isInstance)
            .map(error -> errors.getName() + "." + ((FieldError) error).getField())
            .toList();
    }

    @Configuration
    @EnableConfigurationProperties(ReviewImageProperties.class)
    static class AppProperties {
    }
}
