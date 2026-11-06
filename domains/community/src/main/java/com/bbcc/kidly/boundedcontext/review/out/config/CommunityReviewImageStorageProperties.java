package com.bbcc.kidly.boundedcontext.review.out.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 리뷰 사진 저장소(S3) 설정(kidly.community.review.image.*). 값이 비었거나 형식이 틀리면 키 이름이 나오는 오류로 기동에 실패한다.
 * 장수 · 크기 규칙은 app의 ReviewImageProperties가 같은 키 아래에서 따로 읽는다.
 *
 * @param bucket 사진을 올릴 버킷 이름
 * @param region 버킷이 있는 리전(예: ap-northeast-2)
 * @param keyPrefix 객체 key 맨 앞의 환경 이름(local · dev · prod 등). 환경들이 한 버킷을 경로로만 나눠 쓰므로
 *     / · . 같은 글자로 다른 환경 경로를 가리키지 못하게 소문자만 받는다
 * @param fakeCredentials true면 AWS 자격 증명 대신 가짜 값으로 서명한다(로컬 · 테스트). 주소 모양은 같지만 실제로 올라가지 않는다
 * @param uploadUrlExpiry 업로드 주소 서명 유효 시간(예: PT5M)
 */
@Validated
@ConfigurationProperties("kidly.community.review.image")
public record CommunityReviewImageStorageProperties(
    @NotBlank String bucket,
    @NotBlank String region,
    @NotBlank @Pattern(regexp = "[a-z]+") String keyPrefix,
    boolean fakeCredentials,
    @NotNull @DurationMin(seconds = 1) Duration uploadUrlExpiry) {
}
