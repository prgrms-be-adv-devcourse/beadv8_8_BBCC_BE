package com.bbcc.kidly.boundedcontext.activity.domain;

/**
 * 활동 속성 키(표 community.activity의 attributes JSON 키 = key()). 키 이름은 여기에만 둔다.
 * 회원 ID · 닉네임 · 본문 · 사진 주소 같은 개인정보 · 자유 문구 키는 두지 않는다.
 */
public enum ActivityAttribute {
    // 주문상품 ID
    ORDER_ITEM_ID("order_item_id"),
    // 별점(1~5)
    RATING("rating"),
    // 사이즈 핏(SMALL · TRUE_TO_SIZE · LARGE, 없으면 null)
    FIT("fit"),
    // 사진 장수
    IMAGE_COUNT("image_count"),
    // 수정에서 본문이 바뀌었는지
    CONTENT_CHANGED("content_changed"),
    // 수정에서 사진 목록이 바뀌었는지
    IMAGES_CHANGED("images_changed"),
    // 삭제 때 공개 상태였는지(상품 요약 집계에 들어 있었는지)
    WAS_PUBLIC("was_public"),
    // 찜이 지워진 까닭(user · withdrawn)
    CAUSE("cause"),
    // 검사 상태를 바꾼 쪽(moderator · admin)
    BY("by"),
    // 바뀐 검사 상태(PASSED · HIDDEN · CONFIRMED_HIDDEN · RESTORED)
    STATUS("status"),
    // 걸린 이유 분류(없으면 null)
    REASON("reason"),
    // 기록을 시작하기 전 원본에서 채운 행(마이그레이션 백필만 넣는다)
    BACKFILLED("backfilled");

    private final String key;

    ActivityAttribute(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }
}
