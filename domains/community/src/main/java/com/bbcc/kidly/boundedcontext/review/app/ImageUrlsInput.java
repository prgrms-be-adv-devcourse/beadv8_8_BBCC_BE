package com.bbcc.kidly.boundedcontext.review.app;

import java.util.List;

/**
 * 요청의 사진 주소 목록. 키가 없으면 absent(작성은 사진 없음, 수정은 그대로), 문자열 목록이면 of(빈 목록 = 사진 없음 · 모두 지움),
 * 목록이 아니거나 문자열 아닌 항목이 있으면 invalid(INVALID_IMAGE_URL).
 */
public record ImageUrlsInput(boolean given, boolean valid, List<String> urls) {

    public static ImageUrlsInput absent() {
        return new ImageUrlsInput(false, true, List.of());
    }

    public static ImageUrlsInput of(List<String> urls) {
        return new ImageUrlsInput(true, true, urls);
    }

    public static ImageUrlsInput invalid() {
        return new ImageUrlsInput(true, false, List.of());
    }
}
