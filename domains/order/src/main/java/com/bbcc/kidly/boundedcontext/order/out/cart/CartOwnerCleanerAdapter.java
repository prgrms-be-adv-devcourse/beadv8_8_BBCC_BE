package com.bbcc.kidly.boundedcontext.order.out.cart;

import com.bbcc.kidly.boundedcontext.cart.app.port.in.CartCleanup;
import com.bbcc.kidly.boundedcontext.order.app.port.CartOwnerCleaner;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 장바구니 정리 입구(owner 기준 삭제, #1001)를 부른다. 장바구니 입구(app/port/in)는 인터페이스 · record만 두는 규칙이라 주인 종류를
 * 이름(문자열)으로 받으므로(CartReservations.Payer와 같음) 여기서 enum 이름으로 옮긴다. 같은 앱 안의 동기 호출이라 부르는 쪽 트랜잭션에 합류한다.
 */
@Component
@RequiredArgsConstructor
public class CartOwnerCleanerAdapter implements CartOwnerCleaner {

    private final CartCleanup cartCleanup;

    @Override
    public int removeAll(OwnerType ownerType, Long ownerId) {
        return cartCleanup.removeOwnerItems(ownerType.name(), ownerId);
    }
}
