package com.bbcc.kidly.boundedcontext.review.in.web.dto;

import com.bbcc.kidly.boundedcontext.review.app.ReviewImageUploadService.IssuedUploadUrl;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 리뷰 사진 업로드 주소(요청 순서대로).
 */
public record ReviewImageUploadResponse(List<File> files) {

    public static ReviewImageUploadResponse from(List<IssuedUploadUrl> issued) {
        return new ReviewImageUploadResponse(issued.stream()
            .map(url -> new File(url.uploadUrl(), url.imageUrl(), url.expiresAt()))
            .toList());
    }

    /**
     * @param uploadUrl 이 주소로 PUT 해 사진을 올린다(요청한 Content-Type · 크기 그대로)
     * @param imageUrl 올린 뒤 리뷰 작성 · 수정의 imageUrls에 넣을 공개 주소
     * @param expiresAt uploadUrl 만료 시각(한국 시간). 지나면 다시 발급받는다
     */
    public record File(String uploadUrl, String imageUrl, LocalDateTime expiresAt) {
    }
}
