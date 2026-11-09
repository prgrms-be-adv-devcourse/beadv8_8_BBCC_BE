package com.bbcc.kidly.boundedcontext.wish.out.cart;

import com.bbcc.kidly.boundedcontext.cart.app.port.in.CartReservations;
import com.bbcc.kidly.boundedcontext.wish.app.port.WishApprovers.Principal;
import com.bbcc.kidly.boundedcontext.wish.app.port.WishCart;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 장바구니 칸 예약 입구(#998)를 부른다(#999). 같은 앱 안의 동기 호출이라 부르는 쪽 트랜잭션에 합류한다.
 * 주체 종류는 이름(MEMBER · CHILD)으로 넘긴다(장바구니 주인 종류와 같은 값).
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "kidly.order.wish.enabled", havingValue = "true", matchIfMissing = true)
public class WishCartAdapter implements WishCart {

    private final CartReservations cartReservations;

    @Override
    public List<ReservedLine> reserveOwned(Principal owner, List<Long> cartItemIds, List<Principal> payers,
        String reservationId) {
        return cartReservations.reserveOwned(toPayer(owner), cartItemIds,
                payers.stream().map(WishCartAdapter::toPayer).toList(), reservationId).stream()
            .map(line -> new ReservedLine(line.cartItemId(), line.optionId(), line.childId(), line.quantity()))
            .toList();
    }

    @Override
    public List<Long> reservedCartItemIds(String reservationId) {
        return cartReservations.reservedCartItemIds(reservationId);
    }

    @Override
    public boolean hasPurchase(String reservationId) {
        return cartReservations.hasPurchase(reservationId);
    }

    @Override
    public void release(String reservationId) {
        cartReservations.release(reservationId);
    }

    private static CartReservations.Payer toPayer(Principal principal) {
        return new CartReservations.Payer(principal.type().name(), principal.id());
    }
}
