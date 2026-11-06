package com.bbcc.kidly.boundedcontext.review.out.config;

import com.bbcc.kidly.boundedcontext.review.app.port.ReviewImageStorage;
import com.bbcc.kidly.boundedcontext.review.out.storage.S3ReviewImageStorage;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * 리뷰 사진 저장소(S3 서명). 서버는 EC2 역할의 자격 증명을, 로컬 · 테스트는 가짜 자격 증명(fake-credentials)을 쓴다.
 */
@Configuration
@EnableConfigurationProperties(CommunityReviewImageStorageProperties.class)
public class CommunityReviewImageStorageConfig {

    @Bean
    S3Presigner communityReviewImageS3Presigner(CommunityReviewImageStorageProperties properties) {
        return S3Presigner.builder()
            .region(Region.of(properties.region()))
            .credentialsProvider(credentialsProvider(properties))
            .build();
    }

    @Bean
    ReviewImageStorage communityReviewImageStorage(S3Presigner communityReviewImageS3Presigner,
        CommunityReviewImageStorageProperties properties) {
        return new S3ReviewImageStorage(communityReviewImageS3Presigner, properties);
    }

    private static AwsCredentialsProvider credentialsProvider(CommunityReviewImageStorageProperties properties) {
        if (properties.fakeCredentials()) {
            return StaticCredentialsProvider.create(AwsBasicCredentials.create("local", "local"));
        }
        return DefaultCredentialsProvider.builder().build();
    }
}
