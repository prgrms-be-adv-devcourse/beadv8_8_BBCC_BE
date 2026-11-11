package com.bbcc.kidly.boundedcontext.activity.app.port.in;

import java.util.List;

/**
 * 활동 기록(다른 컨텍스트가 자기 out 어댑터에서 부른다). 부르는 쪽 트랜잭션 안에서만 부를 수 있고(밖이면 실패), 그 트랜잭션과 함께
 * 커밋 · 롤백된다. 흐름 카운터는 커밋 뒤에만 오른다. 실제로 바뀐 것이 있을 때만 부른다(멱등 요청 · 반영하지 않은 결과는 부르지 않는다).
 * 회원 ID · 닉네임 · 본문 · 사진 주소는 받지 않는다.
 */
public interface ActivityRecorder {

    void record(EligibilityCreated activity);

    void record(ReviewCreated activity);

    void record(ReviewUpdated activity);

    void record(ReviewDeleted activity);

    void record(FavoriteAdded activity);

    /**
     * 회원이 찜을 지웠다.
     */
    void record(FavoriteRemoved activity);

    /**
     * 회원 탈퇴로 그 회원의 찜을 지웠다(지운 행마다 하나, 한 문장으로 넣는다).
     */
    void recordWithdrawn(List<FavoriteRemoved> activities);

    void record(ModerationDecided activity);

    record EligibilityCreated(long eligibilityId, Long productId, long orderItemId) {
    }

    /**
     * @param fit 사이즈 핏 이름(없으면 null)
     */
    record ReviewCreated(long reviewId, Long productId, long orderItemId, int rating, String fit, int imageCount) {
    }

    /**
     * 수정 뒤 값과 본문 · 사진이 바뀌었는지.
     */
    record ReviewUpdated(long reviewId, Long productId, int rating, String fit, int imageCount, boolean contentChanged,
                         boolean imagesChanged) {
    }

    /**
     * @param wasPublic 지우기 전 공개 상태였는지
     */
    record ReviewDeleted(long reviewId, Long productId, int rating, boolean wasPublic) {
    }

    record FavoriteAdded(long favoriteId, Long productId) {
    }

    record FavoriteRemoved(long favoriteId, Long productId) {
    }

    /**
     * @param byAdmin 관리자 조치면 true, 검사 결과면 false
     * @param status 바뀐 검사 상태 이름(PASSED · HIDDEN · CONFIRMED_HIDDEN · RESTORED)
     * @param reason 걸린 이유 분류 이름(없으면 null)
     */
    record ModerationDecided(long reviewId, Long productId, boolean byAdmin, String status, String reason) {
    }
}
