package com.bbcc.kidly.boundedcontext.cart.app.port.in;

import java.util.List;

/**
 * 주문하기가 장바구니 칸으로 주문해도 되는지 확인하는 입구(#998). 구현은 장바구니 app이고 부르는 쪽 트랜잭션에 합류한다.
 */
public interface CartCheckout {

    /**
     * 칸마다 '구매자의 칸'이거나 '구매자가 허용된 예약 칸'이고, 옵션 · 입을 자녀가 칸과 같아야 한다.
     * 칸이 없거나 다른 가족의 칸이고 허용 주체도 아니면 CART_ITEM_NOT_FOUND(404, 존재를 숨김), 같은 가족인데 허용되지 않았거나
     * 옵션 · 자녀가 다르면 CART_ITEM_NOT_ORDERABLE(403).
     *
     * @param buyer 주문하는 주체(지금은 회원)
     */
    void requireOrderable(CartReservations.Payer buyer, List<Line> lines);

    /**
     * @param childId 입을 자녀. 없으면 null
     */
    record Line(Long cartItemId, Long optionId, Long childId) {
    }
}
