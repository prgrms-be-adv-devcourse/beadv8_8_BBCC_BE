package com.bbcc.kidly.boundedcontext.cart.app.port.in;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 다른 영역이 장바구니 칸을 예약하는 입구(#998). 예약된 칸은 주인이 빼거나 수량을 바꿀 수 없고(409), 결제 허용 주체만 그 칸으로
 * 주문할 수 있다. 결제 정리는 주문에 기록된 칸을 지운다(같은 가족이면 예약 상태와 상관없이). 그래서 예약 해제는 결제 정리 전후
 * 어느 쪽이어도 된다. 장바구니는 예약한 쪽이 누구인지 모르고 예약 ID(불투명 문자열)만 기억한다. 구현은 장바구니 app이고 부르는 쪽
 * 트랜잭션에 합류한다.
 */
public interface CartReservations {

    /**
     * 칸들을 예약한다. 같은 예약 ID로 다시 부르면 칸 · 허용 주체를 더한다(이미 있는 것은 그대로, 기한은 이번 값으로 맞춘다).
     * 칸이 없으면 CART_ITEM_NOT_FOUND, 다른 예약에 든 칸이면 CART_ITEM_RESERVED(409), 빈 목록 · 예약 ID면 INVALID_RESERVATION.
     *
     * @param reservationId 예약한 쪽이 정한 ID(64자 이하)
     * @param validUntil 결제 가능 기한(#1008). 이 시각과 같거나 지나면 허용 주체도 주문할 수 없다(RESERVATION_EXPIRED 409).
     *     null이면 기한 없음
     */
    void reserve(List<Long> cartItemIds, List<Payer> payers, String reservationId, LocalDateTime validUntil);

    /**
     * 주인의 칸만 예약하고(#999) 예약한 칸의 지금 값을 고른 순서로 돌려준다. 주인의 칸이 아니면(없는 칸 포함) CART_ITEM_NOT_FOUND(404,
     * 남의 칸이 있는지 알리지 않음). 그 밖의 규칙은 {@link #reserve}와 같다.
     *
     * @param owner 칸 주인(장바구니 주인 종류 MEMBER · CHILD와 ID)
     */
    List<ReservedLine> reserveOwned(Payer owner, List<Long> cartItemIds, List<Payer> payers, String reservationId,
        LocalDateTime validUntil);

    /**
     * 결제 가능 기한(#1008)을 바꾼다. 예약마다 값이 다를 수 있어 (예약 ID, 기한) 여러 개를 한 번에 받는다. 기한 null은 기한 없음.
     * 없는 예약은 건너뛴다(그 사이 풀린 예약).
     *
     * @return 실제로 값이 바뀐 예약 수
     */
    int updateValidUntil(List<ValidUntil> changes);

    /**
     * 이 예약에 아직 남아 있는 칸 ID(#999). 예약 칸은 주인이 뺄 수 없으므로, 예약한 칸보다 적으면 빠진 칸은 결제 정리로 빠진 것이다.
     * 없는 예약이면 빈 목록.
     */
    List<Long> reservedCartItemIds(String reservationId);

    /**
     * 결제 정리가 이 예약의 칸을 정리한 적이 있는지(#999). 칸이 남는 일부 수량 결제도 포함한다. 없는 예약(풀린 예약 포함)이면 false.
     */
    boolean hasPurchase(String reservationId);

    /**
     * 예약을 푼다(칸은 남는다). 없는 예약이면 아무것도 하지 않는다.
     */
    void release(String reservationId);

    /**
     * 결제 허용 주체.
     *
     * @param type MEMBER · CHILD
     * @param id 회원 ID 또는 자녀 프로필 ID
     */
    record Payer(String type, Long id) {
    }

    /**
     * 예약의 결제 가능 기한(#1008).
     *
     * @param validUntil null이면 기한 없음
     */
    record ValidUntil(String reservationId, LocalDateTime validUntil) {
    }

    /**
     * 예약한 칸의 지금 값.
     *
     * @param childId 입을 자녀. 없으면 null
     */
    record ReservedLine(Long cartItemId, Long optionId, Long childId, int quantity) {
    }
}
