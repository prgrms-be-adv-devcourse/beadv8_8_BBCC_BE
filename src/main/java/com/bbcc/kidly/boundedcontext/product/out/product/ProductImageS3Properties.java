package com.bbcc.kidly.boundedcontext.product.out.product;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 상품 이미지를 올리는 S3 설정. 값이 비었거나 형식이 맞지 않으면 기동에 실패한다.
 *
 * @param bucket 이미지를 올릴 버킷 이름
 * @param region 버킷이 있는 리전(예: ap-northeast-2)
 * @param keyPrefix 객체 key의 맨 앞 경로로 쓰는 환경 이름. dev와 prod가 한 버킷을 경로로만 나눠 쓰므로
 *     /같은 글자로 다른 환경 경로를 가리키지 못하게 소문자만 받는다
 * @param fakeCredentials true면 AWS 자격 증명 대신 가짜 값으로 서명한다. 로컬에서 AWS 없이 주소를 만들 때 쓴다
 */
@Validated
@ConfigurationProperties("kidly.product.image.s3")
public record ProductImageS3Properties(
    @NotBlank String bucket,
    @NotBlank String region,
    @NotBlank @Pattern(regexp = "[a-z]+") String keyPrefix,
    boolean fakeCredentials) {
}
