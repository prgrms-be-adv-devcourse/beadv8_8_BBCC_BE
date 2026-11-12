package com.bbcc.kidly.boundedcontext.review.app.port;

/**
 * 리뷰 쪽 활동 기록(작성 자격 · 리뷰 · 검사). 원래 변경과 같은 트랜잭션 안에서, 실제로 바뀐 것이 있을 때만 부른다.
 * 회원 ID · 닉네임 · 본문 · 사진 주소는 넘기지 않는다. 구현은 out/activity(활동 컨텍스트의 들어오는 포트를 부른다).
 */
public interface ReviewActivityLog {

    void eligibilityCreated(Long eligibilityId, Long productId, Long orderItemId);

    /**
     * @param fit 사이즈 핏 이름(없으면 null)
     */
    void reviewCreated(Long reviewId, Long productId, Long orderItemId, int rating, String fit, int imageCount);

    /**
     * 수정 뒤 값과 본문 · 사진이 바뀌었는지.
     *
     * @param visible 수정 뒤 공개 상태인지
     */
    void reviewUpdated(Long reviewId, Long productId, Long orderItemId, int rating, String fit, int imageCount,
        boolean contentChanged, boolean imagesChanged, boolean visible);

    /**
     * @param wasPublic 지우기 전 공개 상태였는지
     */
    void reviewDeleted(Long reviewId, Long productId, Long orderItemId, int rating, String fit, boolean wasPublic);

    /**
     * @param rating · fit 결정 때 리뷰 값(이벤트 본문용)
     * @param byAdmin 관리자 조치면 true, 검사 결과면 false
     * @param status 바뀐 검사 상태 이름
     * @param reason 걸린 이유 분류 이름(없으면 null)
     * @param visible 결정 뒤 리뷰가 공개인지({@code Review.isPublic()})
     */
    void moderationDecided(Long reviewId, Long productId, Long orderItemId, int rating, String fit, boolean byAdmin,
        String status, String reason, boolean visible);
}
