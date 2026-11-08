package com.bbcc.kidly.boundedcontext.review.domain;

import java.util.Arrays;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 업로드 주소를 받을 사진 한 장(형식 · 크기)과 저장 경로 규칙.
 * 경로는 {환경}/reviews/{회원 ID}/{UUID}.{확장자}이고, 이름(UUID.확장자)은 서버가 만든다.
 *
 * @param contentLength 바이트 수. 서명에 넣어 이 크기로만 올라가게 한다
 */
public record ReviewImageUpload(ReviewImageFormat format, long contentLength) {

    // 서버가 만든 이름: 소문자 UUID + 받는 형식의 확장자
    private static final Pattern GENERATED_NAME = Pattern.compile(
        "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(?:"
            + Arrays.stream(ReviewImageFormat.values()).map(ReviewImageFormat::extension)
                .collect(Collectors.joining("|"))
            + ")");

    /**
     * @param maxBytes 한 장 최대 크기(설정 값)
     * @throws ReviewRuleException 형식이 jpg · png · webp가 아니거나 크기가 1 ~ maxBytes 밖이면 IMAGE_FILE
     */
    public static ReviewImageUpload of(String contentType, Long contentLength, long maxBytes) {
        ReviewImageFormat format = ReviewImageFormat.ofContentType(contentType)
            .orElseThrow(() -> new ReviewRuleException(ReviewRule.IMAGE_FILE));
        if (contentLength == null || contentLength < 1 || contentLength > maxBytes) {
            throw new ReviewRuleException(ReviewRule.IMAGE_FILE);
        }
        return new ReviewImageUpload(format, contentLength);
    }

    public String key(String keyPrefix, long memberId, UUID name) {
        return directory(keyPrefix, memberId) + name + "." + format.extension();
    }

    /**
     * 회원 한 명의 리뷰 사진 경로({환경}/reviews/{회원 ID}/). 본인 사진인지 이 경로로 판별한다.
     */
    public static String directory(String keyPrefix, long memberId) {
        return reviewsDirectory(keyPrefix) + memberId + "/";
    }

    /**
     * 이 환경의 리뷰 사진 경로({환경}/reviews/). 정리할 때 이 경로 아래만 읽는다.
     */
    public static String reviewsDirectory(String keyPrefix) {
        return keyPrefix + "/reviews/";
    }

    /**
     * 서버가 만든 이름(UUID.확장자)인지. 슬래시 · 점 두 개 · 인코딩 글자 · 쿼리가 섞이면 아니다.
     */
    public static boolean isGeneratedName(String name) {
        return name != null && GENERATED_NAME.matcher(name).matches();
    }
}
