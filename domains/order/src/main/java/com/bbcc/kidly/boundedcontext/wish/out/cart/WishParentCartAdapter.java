package com.bbcc.kidly.boundedcontext.wish.out.cart;

import com.bbcc.kidly.boundedcontext.cart.app.port.in.CartIntake;
import com.bbcc.kidly.boundedcontext.wish.app.port.WishParentCart;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 승인한 조르기 줄을 장바구니 컨텍스트의 담기 포트로 보호자 장바구니에 담는다(#701 · #641). 같은 앱 안의 동기 호출이라
 * 부르는 쪽 트랜잭션에 합류한다.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "kidly.order.wish.enabled", havingValue = "true", matchIfMissing = true)
public class WishParentCartAdapter implements WishParentCart {

    private final CartIntake cartIntake;

    @Override
    public void put(Long memberId, Long optionId, Long childId, int quantity) {
        cartIntake.add(memberId, optionId, childId, quantity);
    }
}
