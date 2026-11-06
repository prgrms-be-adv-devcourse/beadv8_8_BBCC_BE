package com.bbcc.kidly.boundedcontext.review.in.web.dto;

import com.bbcc.kidly.boundedcontext.review.app.ReviewService.ImageUrlsInput;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;

/**
 * 요청 본문의 imageUrls를 JSON 원래 타입 그대로 받아 구분한다: 키 없음 · 문자열 목록 · 그 밖(400 INVALID_IMAGE_URL).
 * null은 작성에서는 사진 없음(키 없음과 같다), 수정에서는 틀린 값(목록을 지우려면 빈 목록을 보낸다)이다.
 */
public final class ImageUrlsRequest {

    private ImageUrlsRequest() {
    }

    public static ImageUrlsInput forWrite(JsonNode imageUrls) {
        if (imageUrls == null || imageUrls.isMissingNode() || imageUrls.isNull()) {
            return ImageUrlsInput.absent();
        }
        return toInput(imageUrls);
    }

    public static ImageUrlsInput forUpdate(JsonNode imageUrls) {
        if (imageUrls == null || imageUrls.isMissingNode()) {
            return ImageUrlsInput.absent();
        }
        return toInput(imageUrls);
    }

    private static ImageUrlsInput toInput(JsonNode imageUrls) {
        if (!imageUrls.isArray()) {
            return ImageUrlsInput.invalid();
        }
        List<String> urls = new ArrayList<>();
        for (JsonNode url : imageUrls) {
            if (!url.isString()) {
                return ImageUrlsInput.invalid();
            }
            urls.add(url.stringValue());
        }
        return ImageUrlsInput.of(urls);
    }
}
