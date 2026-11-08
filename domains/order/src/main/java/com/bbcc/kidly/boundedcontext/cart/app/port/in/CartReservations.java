package com.bbcc.kidly.boundedcontext.cart.app.port.in;

import java.util.List;

/**
 * 다른 영역이 장바구니 칸을 예약하는 입구(#998). 예약된 칸은 주인이 빼거나 수량을 바꿀 수 없고(409), 결제 허용 주체만 그 칸으로
 * 주문할 수 있다. 결제 정리는 주문에 기록된 칸을 지운다(같은 가족이면 예약 상태와 상관없이). 그래서 예약 해제는 결제 정리 전후
 * 어느 쪽이어도 된다. 장바구니는 예약한 쪽이 누구인지 모르고 예약 ID(불투명 문자열)만 기억한다. 구현은 장바구니 app이고 부르는 쪽
 * 트랜잭션에 합류한다.
 */
public interface CartReservations {

    /**
     * 칸들을 예약한다. 같은 예약 ID로 다시 부르면 칸 · 허용 주체를 더한다(이미 있는 것은 그대로).
     * 칸이 없으면 CART_ITEM_NOT_FOUND, 다른 예약에 든 칸이면 CART_ITEM_RESERVED(409), 빈 목록 · 예약 ID면 INVALID_RESERVATION.
     *
     * @param reservationId 예약한 쪽이 정한 ID(64자 이하)
     */
    void reserve(List<Long> cartItemIds, List<Payer> payers, String reservationId);

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
}
