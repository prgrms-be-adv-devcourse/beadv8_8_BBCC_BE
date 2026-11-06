package com.bbcc.kidly.boundedcontext.review.app;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

/**
 * 리뷰 사진 규칙 설정(kidly.community.review.image.*). 값만 바꿔 장수 · 크기를 조절한다. 틀리면 기동에 실패한다.
 * 저장소(버킷 · 경로 · 서명) 설정은 out/config의 CommunityReviewImageStorageProperties가 같은 키 아래에서 따로 읽는다.
 *
 * @param maxCount 리뷰 한 건의 최대 사진 장수(업로드 주소 발급 · 작성 · 수정). 1 ~ 100
 * @param maxFileSize 사진 한 장 최대 크기(예: 10MB)
 */
@Validated
@ConfigurationProperties("kidly.community.review.image")
public record ReviewImageProperties(
    @Min(1) @Max(100) int maxCount,
    @NotNull DataSize maxFileSize) {

    @AssertTrue(message = "max-file-size는 1바이트 이상이어야 합니다")
    public boolean isMaxFileSizePositive() {
        return maxFileSize == null || maxFileSize.toBytes() > 0;
    }
}
