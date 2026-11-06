package com.bbcc.kidly.boundedcontext.review.domain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 리뷰에 붙이는 사진 주소 목록(보낸 순서). 업로드 주소 발급으로 받은 본인 주소만 받는다:
 * 본인 경로 공개 주소(ownUrlPrefix)로 시작하고 나머지가 서버가 만든 이름(UUID.확장자) 하나뿐인 것. 같은 주소는 한 번만.
 * 업로드됐는지(저장소에 실제로 있는지)는 보지 않는다.
 */
public record ReviewImageUrls(List<String> urls) {

    public ReviewImageUrls {
        urls = List.copyOf(urls);
    }

    public static ReviewImageUrls none() {
        return new ReviewImageUrls(List.of());
    }

    /**
     * 장수를 먼저 보고(넘으면 TOO_MANY_IMAGES) 주소를 하나씩 본다(아니면 IMAGE_URL).
     *
     * @param ownUrlPrefix 로그인 회원의 사진 공개 주소 접두어(https://{버킷 주소}/{환경}/reviews/{회원 ID}/)
     * @param maxCount 최대 장수(설정 값)
     */
    public static ReviewImageUrls of(List<String> urls, String ownUrlPrefix, int maxCount) {
        if (urls == null) {
            throw new ReviewRuleException(ReviewRule.IMAGE_URL);
        }
        if (urls.size() > maxCount) {
            throw new ReviewRuleException(ReviewRule.TOO_MANY_IMAGES);
        }
        Set<String> seen = new HashSet<>();
        for (String url : urls) {
            if (!isOwn(url, ownUrlPrefix) || !seen.add(url)) {
                throw new ReviewRuleException(ReviewRule.IMAGE_URL);
            }
        }
        return new ReviewImageUrls(urls);
    }

    /**
     * 보낸 순서대로 0부터 순서를 매긴 사진 행.
     */
    public List<ReviewImage> toImages(Long reviewId) {
        List<ReviewImage> images = new ArrayList<>();
        for (int position = 0; position < urls.size(); position++) {
            images.add(ReviewImage.of(reviewId, position, urls.get(position)));
        }
        return images;
    }

    private static boolean isOwn(String url, String ownUrlPrefix) {
        return url != null && url.startsWith(ownUrlPrefix)
            && ReviewImageUpload.isGeneratedName(url.substring(ownUrlPrefix.length()));
    }
}
