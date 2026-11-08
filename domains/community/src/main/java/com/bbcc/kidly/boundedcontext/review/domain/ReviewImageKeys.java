package com.bbcc.kidly.boundedcontext.review.domain;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 정리(저장소에서 지우기)할 수 있는 리뷰 사진 key 규칙. 환경들이 한 버킷을 경로로만 나눠 쓰므로, 이 환경의 리뷰 경로에 서버가 만든 이름으로
 * 올라간 사진만 지운다: {이 환경 key-prefix}/reviews/{양수 회원 ID}/{소문자 UUID}.{jpg|png|webp}. 다른 환경 · 다른 기능의 파일 ·
 * 경로 조작(.. · 인코딩 · 슬래시 추가)은 모두 아니다.
 */
public final class ReviewImageKeys {

    private static final Pattern MEMBER_AND_NAME = Pattern.compile("([1-9][0-9]{0,18})/([^/]+)");

    private ReviewImageKeys() {
    }

    /**
     * @param keyPrefix 이 환경의 key 맨 앞 경로(소문자만, 설정 값)
     */
    public static boolean isCleanable(String keyPrefix, String key) {
        if (keyPrefix == null || keyPrefix.isEmpty() || key == null) {
            return false;
        }
        String directory = ReviewImageUpload.reviewsDirectory(keyPrefix);
        if (!key.startsWith(directory)) {
            return false;
        }
        Matcher matcher = MEMBER_AND_NAME.matcher(key.substring(directory.length()));
        return matcher.matches() && isLong(matcher.group(1)) && ReviewImageUpload.isGeneratedName(matcher.group(2));
    }

    private static boolean isLong(String digits) {
        try {
            Long.parseLong(digits);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
