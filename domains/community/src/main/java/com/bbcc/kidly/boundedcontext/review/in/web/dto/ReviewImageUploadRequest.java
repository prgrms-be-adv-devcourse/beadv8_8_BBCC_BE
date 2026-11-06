package com.bbcc.kidly.boundedcontext.review.in.web.dto;

import com.bbcc.kidly.boundedcontext.review.app.ReviewImageUploadService.FileSpec;
import java.util.List;

/**
 * 리뷰 사진 업로드 주소 발급 요청. 값 확인은 서비스가 한다(틀리면 400 INVALID_IMAGE_FILE · TOO_MANY_IMAGES).
 *
 * @param files 올릴 파일(1장 ~ 최대 장수). 응답은 이 순서대로다
 */
public record ReviewImageUploadRequest(List<File> files) {

    public List<FileSpec> toSpecs() {
        return files == null ? null
            : files.stream().map(file -> file == null ? null : new FileSpec(file.contentType(), file.contentLength()))
                .toList();
    }

    /**
     * @param contentType image/jpeg · image/png · image/webp. 업로드할 때 이 값을 Content-Type으로 그대로 보낸다
     * @param contentLength 파일 바이트 수(1 ~ 10MB). 업로드할 때 이 크기 그대로 보낸다
     */
    public record File(String contentType, Long contentLength) {
    }
}
