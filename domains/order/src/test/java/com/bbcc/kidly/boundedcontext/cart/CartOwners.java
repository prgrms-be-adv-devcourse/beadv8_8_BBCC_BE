package com.bbcc.kidly.boundedcontext.cart;

import com.bbcc.kidly.boundedcontext.cart.app.port.CartActors;
import com.bbcc.kidly.boundedcontext.cart.domain.CartItem;
import com.bbcc.kidly.boundedcontext.cart.domain.CartItem.OwnerType;

/**
 * 테스트용 장바구니 주인(#998). 회원 장바구니는 주인 = 결제 회원이다.
 */
public final class CartOwners {

    private CartOwners() {
    }

    public static CartActors.Actor member(Long memberId) {
        return new CartActors.Actor(OwnerType.MEMBER, memberId, memberId);
    }

    public static CartActors.Actor child(Long childProfileId, Long guardianId) {
        return new CartActors.Actor(OwnerType.CHILD, childProfileId, guardianId);
    }

    /**
     * 회원 장바구니 줄.
     */
    public static CartItem memberLine(Long memberId, Long optionId, Long childId, int quantity) {
        return CartItem.create(OwnerType.MEMBER, memberId, memberId, optionId, childId, quantity);
    }
}
