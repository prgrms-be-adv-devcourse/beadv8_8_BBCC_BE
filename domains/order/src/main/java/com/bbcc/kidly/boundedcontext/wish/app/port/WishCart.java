package com.bbcc.kidly.boundedcontext.wish.app.port;

import com.bbcc.kidly.boundedcontext.wish.app.port.WishApprovers.Principal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * 조르기가 장바구니 칸을 예약 · 해제한다(#999). 구현은 out의 장바구니 컨텍스트 어댑터(장바구니 칸 예약 입구)이고 부르는 쪽 트랜잭션에
 * 합류한다. 장바구니가 거절하면(남의 칸 404 · 이미 예약된 칸 409 등) 장바구니의 업무 예외가 그대로 나간다(조르기도 함께 롤백).
 */
public interface WishCart {

    /**
     * 주인의 칸만 예약하고 예약한 칸의 지금 값을 돌려준다(고른 순서).
     *
     * @param payers 결제 허용 주체(승인자들)
     * @param reservationId 조르기 예약 ID
     * @param validUntil 장바구니 예약의 결제 가능 기한(#1008, 조르기 만료 시각의 사본). null이면 기한 없음
     */
    List<ReservedLine> reserveOwned(Principal owner, List<Long> cartItemIds, List<Principal> payers,
        String reservationId, LocalDateTime validUntil);

    /**
     * 예약들의 결제 가능 기한을 바꾼다(#1008, 기한 설정이 바뀌면 다시 계산한 값). 없는 예약은 건너뛴다.
     *
     * @return 실제로 값이 바뀐 예약 수
     */
    int updateValidUntil(List<ValidUntil> changes);

    /**
     * 이 예약에 아직 남은 칸 ID. 예약 칸은 주인이 뺄 수 없으므로 줄보다 적으면 결제 정리로 빠진 칸이 있다.
     */
    List<Long> reservedCartItemIds(String reservationId);

    /**
     * 결제 정리가 이 예약의 칸을 정리한 적이 있는지(칸이 남는 일부 수량 결제 포함). 없는 예약이면 false.
     */
    boolean hasPurchase(String reservationId);

    /**
     * 조르기 예약을 잠그고(#1207) 결제 정리 표시가 있는지 돌려준다. 조르기 상태를 읽거나 바꾸기 전에 먼저 부른다: 결제 정리 · 다른 조르기
     * 처리와 같은 순서(장바구니 예약 → 조르기 → 칸)로 잠가 교착하지 않는다. 잠금은 트랜잭션이 끝날 때까지 유지된다. 없는 예약이면 false.
     *
     * @return 결제 정리 표시가 있으면 true
     */
    boolean lock(String reservationId);

    /**
     * 여러 조르기 예약을 한 번에 같은 전체 순서로 잠근다(#1207). 여러 조르기를 다루는 경로(자녀 삭제 철회)가 조르기를 읽기 전에 부른다.
     */
    void lockAll(Collection<String> reservationIds);

    /**
     * 예약을 푼다(칸은 장바구니에 남는다). 없는 예약이면 아무것도 하지 않는다.
     */
    void release(String reservationId);

    /**
     * @param validUntil null이면 기한 없음
     */
    record ValidUntil(String reservationId, LocalDateTime validUntil) {
    }

    /**
     * @param childId 입을 자녀. 없으면 null
     */
    record ReservedLine(Long cartItemId, Long optionId, Long childId, int quantity) {
    }
}
