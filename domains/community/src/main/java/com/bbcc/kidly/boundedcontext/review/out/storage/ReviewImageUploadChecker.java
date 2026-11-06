package com.bbcc.kidly.boundedcontext.review.out.storage;

/**
 * 사진 한 장이 저장소에 있는지 확인하는 방식(설정 kidly.community.review.image.upload-check의 이름으로 고른다).
 * s3(HeadObject) · none(확인 안 함). 예외를 던지지 않는다(확인할 수 없으면 UNKNOWN).
 */
public interface ReviewImageUploadChecker {

    String name();

    /**
     * @param imageUrl 공개 주소
     * @param key 버킷 안 객체 key
     */
    Presence check(String imageUrl, String key);

    enum Presence {
        // 있음
        PRESENT,
        // 없음(올리지 않음)
        ABSENT,
        // 확인할 수 없음(장애 · 시간 초과 · 권한 문제)
        UNKNOWN
    }
}
