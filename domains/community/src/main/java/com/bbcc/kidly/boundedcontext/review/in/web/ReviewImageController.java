package com.bbcc.kidly.boundedcontext.review.in.web;

import com.bbcc.kidly.boundedcontext.review.app.ReviewImageUploadService;
import com.bbcc.kidly.boundedcontext.review.in.web.dto.ReviewImageUploadRequest;
import com.bbcc.kidly.boundedcontext.review.in.web.dto.ReviewImageUploadResponse;
import com.bbcc.kidly.global.security.AuthMember;
import com.bbcc.kidly.global.security.LoginMember;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 리뷰 사진 업로드 주소 발급. 브라우저가 받은 uploadUrl로 S3에 직접 올리고, imageUrl을 리뷰 작성 · 수정의 imageUrls에 넣는다.
 */
@RestController
@RequestMapping("/api/v1/reviews/images")
@RequiredArgsConstructor
public class ReviewImageController {

    private final ReviewImageUploadService uploadService;

    /**
     * 200. 오류: 400 INVALID_IMAGE_FILE(형식 · 크기 · 빈 목록) · TOO_MANY_IMAGES(최대 장수 초과), 401(로그인 없음).
     */
    @PostMapping("/upload-urls")
    public ReviewImageUploadResponse issueUploadUrls(@LoginMember AuthMember member,
        @RequestBody ReviewImageUploadRequest request) {
        return ReviewImageUploadResponse.from(uploadService.issue(member.id(), request.toSpecs()));
    }
}
