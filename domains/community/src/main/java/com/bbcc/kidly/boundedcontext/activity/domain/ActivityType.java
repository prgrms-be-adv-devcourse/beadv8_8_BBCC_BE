package com.bbcc.kidly.boundedcontext.activity.domain;

/**
 * 활동 종류(표 community.activity의 type 값 = 이 이름). 새 사실은 값을 더한다(이름을 바꾸거나 지우지 않는다).
 */
public enum ActivityType {
    // 구매확정으로 리뷰 작성 자격이 새로 생김(대상: 작성 자격 행)
    ELIGIBILITY_CREATED,
    // 리뷰 작성(대상: 리뷰)
    REVIEW_CREATED,
    // 리뷰 수정으로 실제로 바뀐 것이 있음(대상: 리뷰)
    REVIEW_UPDATED,
    // 리뷰 삭제(대상: 리뷰)
    REVIEW_DELETED,
    // 찜이 새로 생김(대상: 찜)
    FAVORITE_ADDED,
    // 찜이 실제로 지워짐(대상: 찜)
    FAVORITE_REMOVED,
    // 검사 결과 · 관리자 조치가 검사 상태를 바꿈(대상: 리뷰)
    MODERATION_DECIDED
}
