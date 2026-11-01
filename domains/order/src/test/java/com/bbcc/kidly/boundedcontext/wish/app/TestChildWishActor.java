package com.bbcc.kidly.boundedcontext.wish.app;

import com.bbcc.kidly.boundedcontext.wish.app.port.WishActor;
import com.bbcc.kidly.boundedcontext.wish.app.port.WishCatalog;
import com.bbcc.kidly.boundedcontext.wish.domain.WishItem.AddedBy;

/**
 * 테스트용 자녀(#640). 자녀 계정 인증이 없어 운영 구현은 없고, 자녀가 부르는 자리를 테스트에서만 채운다.
 * 자기(자녀 프로필 ID) 공용 장바구니만 쓴다.
 */
public record TestChildWishActor(Long childId) implements WishActor {

    @Override
    public AddedBy type() {
        return AddedBy.CHILD;
    }

    @Override
    public Long id() {
        return childId;
    }

    @Override
    public boolean canUse(WishCatalog.Child child) {
        return !child.deleted() && child.childId().equals(childId);
    }
}
