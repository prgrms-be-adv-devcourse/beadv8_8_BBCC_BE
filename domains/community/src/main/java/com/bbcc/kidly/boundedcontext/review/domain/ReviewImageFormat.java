package com.bbcc.kidly.boundedcontext.review.domain;

import java.util.Arrays;
import java.util.Optional;

/**
 * 리뷰 사진으로 받는 형식과 그 확장자. 업로드 주소의 Content-Type과 객체 이름의 확장자가 이 값으로 정해진다.
 */
public enum ReviewImageFormat {
    JPEG("image/jpeg", "jpg"),
    PNG("image/png", "png"),
    WEBP("image/webp", "webp");

    private final String contentType;
    private final String extension;

    ReviewImageFormat(String contentType, String extension) {
        this.contentType = contentType;
        this.extension = extension;
    }

    public String contentType() {
        return contentType;
    }

    public String extension() {
        return extension;
    }

    /**
     * Content-Type이 정확히 같은 형식(소문자, 매개변수 없음). 서명에 그대로 들어가 업로드 요청의 헤더와 글자까지 같아야 한다.
     */
    public static Optional<ReviewImageFormat> ofContentType(String contentType) {
        return Arrays.stream(values()).filter(format -> format.contentType.equals(contentType)).findFirst();
    }
}
