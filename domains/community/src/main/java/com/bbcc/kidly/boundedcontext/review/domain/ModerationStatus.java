package com.bbcc.kidly.boundedcontext.review.domain;

/**
 * 리뷰 검사 상태. 검사 대기 · 통과 · 다시 공개는 공개, 검사 숨김 · 숨김 확정은 숨김(공개 목록 · 상품 요약에서 빠진다).
 */
public enum ModerationStatus {
    // 저장 · 글 수정 뒤 검사 대기(공개)
    PENDING(true),
    // 검사 통과(공개)
    PASSED(true),
    // 검사에서 걸려 숨김(관리자 판단 대기)
    HIDDEN(false),
    // 관리자가 숨김 확정. 작성자가 고쳐도 숨김 그대로
    CONFIRMED_HIDDEN(false),
    // 관리자가 다시 공개. 글 · 사진을 고치면 다시 검사 대기
    RESTORED(true);

    private final boolean visible;

    ModerationStatus(boolean visible) {
        this.visible = visible;
    }

    public boolean isPublic() {
        return visible;
    }
}
