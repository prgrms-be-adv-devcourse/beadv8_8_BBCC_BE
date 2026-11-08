package com.bbcc.kidly.boundedcontext.review.out.config;

import com.bbcc.kidly.boundedcontext.review.app.port.ReviewImageStorage;
import com.bbcc.kidly.boundedcontext.review.out.storage.NoReviewImageUploadChecker;
import com.bbcc.kidly.boundedcontext.review.out.storage.ReviewImageUploadChecker;
import com.bbcc.kidly.boundedcontext.review.out.storage.S3ReviewImageStorage;
import com.bbcc.kidly.boundedcontext.review.out.storage.S3ReviewImageUploadChecker;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * 리뷰 사진 저장소(S3 서명 · 업로드 확인). 서버는 EC2 역할의 자격 증명을, 로컬 · 테스트는 가짜 자격 증명(fake-credentials)을 쓴다.
 * 업로드 확인 방식은 upload-check의 이름으로 고르고, 모르는 이름이면 그 키 이름이 나오는 오류로 기동에 실패한다.
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(CommunityReviewImageStorageProperties.class)
public class CommunityReviewImageStorageConfig {

    static final String UPLOAD_CHECK_KEY = "kidly.community.review.image.upload-check";

    @Bean
    S3Presigner communityReviewImageS3Presigner(CommunityReviewImageStorageProperties properties) {
        return S3Presigner.builder()
            .region(Region.of(properties.region()))
            .credentialsProvider(credentialsProvider(properties))
            .build();
    }

    @Bean
    ReviewImageUploadChecker communityNoReviewImageUploadChecker() {
        return new NoReviewImageUploadChecker();
    }

    // 업로드 확인이 s3일 때만 동기 S3 클라이언트를 만든다. HTTP 구현은 S3 SDK가 가져온 Apache 5 클라이언트를 자동으로 고른다
    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(name = UPLOAD_CHECK_KEY, havingValue = "s3", matchIfMissing = true)
    S3Client communityReviewImageS3Client(CommunityReviewImageStorageProperties properties) {
        return S3Client.builder()
            .region(Region.of(properties.region()))
            .credentialsProvider(credentialsProvider(properties))
            .overrideConfiguration(config -> config.apiCallTimeout(properties.uploadCheckTimeout()))
            .build();
    }

    @Bean
    @ConditionalOnProperty(name = UPLOAD_CHECK_KEY, havingValue = "s3", matchIfMissing = true)
    ReviewImageUploadChecker communityS3ReviewImageUploadChecker(S3Client communityReviewImageS3Client,
        CommunityReviewImageStorageProperties properties) {
        return new S3ReviewImageUploadChecker(communityReviewImageS3Client, properties.bucket());
    }

    @Bean
    ReviewImageStorage communityReviewImageStorage(S3Presigner communityReviewImageS3Presigner,
        CommunityReviewImageStorageProperties properties, List<ReviewImageUploadChecker> checkers) {
        ReviewImageUploadChecker checker = checkers.stream()
            .filter(candidate -> candidate.name().equals(properties.uploadCheck()))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(UPLOAD_CHECK_KEY + "에 모르는 확인 방식이 있습니다: "
                + properties.uploadCheck() + " (쓸 수 있는 이름: "
                + String.join(", ", checkers.stream().map(ReviewImageUploadChecker::name).sorted().toList()) + ")"));
        if (checker instanceof NoReviewImageUploadChecker) {
            log.info("리뷰 사진 업로드 확인 꺼짐(upload-check: none)");
        }
        return new S3ReviewImageStorage(communityReviewImageS3Presigner, properties, checker);
    }

    private static AwsCredentialsProvider credentialsProvider(CommunityReviewImageStorageProperties properties) {
        if (properties.fakeCredentials()) {
            return StaticCredentialsProvider.create(AwsBasicCredentials.create("local", "local"));
        }
        return DefaultCredentialsProvider.builder().build();
    }
}
