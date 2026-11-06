package com.bbcc.kidly.boundedcontext.review.domain;

/**
 * 리뷰 규칙 위반의 종류.
 */
public enum ReviewRule {
    // 별점은 1~5 정수
    RATING,
    // 본문은 앞뒤 공백을 뺀 1~1000자
    CONTENT,
    // 수정 · 삭제는 작성자만
    AUTHOR,
    // 수정은 바꿀 값(별점 · 본문 · 핏)을 하나 이상 담는다
    NO_CHANGES,
    // 핏은 구매 사이즈가 있는 주문상품에만 남긴다
    FIT_NOT_APPLICABLE,
    // 사진 파일은 jpg · png · webp, 1바이트 이상 최대 크기 이하
    IMAGE_FILE,
    // 사진은 최대 장수까지
    TOO_MANY_IMAGES,
    // 사진 주소는 본인 경로 아래 서버가 만든 이름만, 같은 주소는 한 번
    IMAGE_URL
}
