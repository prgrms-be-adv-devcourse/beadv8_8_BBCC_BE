package com.bbcc.kidly.boundedcontext.wish.out.actor;

import com.bbcc.kidly.boundedcontext.order.api.ChildView;
import com.bbcc.kidly.boundedcontext.wish.app.port.WishActor;
import com.bbcc.kidly.boundedcontext.wish.domain.WishItem.AddedBy;

/**
 * 로그인한 부모(#640). 자녀 복사본의 보호자 회원 ID가 자기인 자녀의 공용 장바구니만 쓴다.
 */
public record ParentWishActor(Long memberId) implements WishActor {

    @Override
    public AddedBy type() {
        return AddedBy.PARENT;
    }

    @Override
    public Long id() {
        return memberId;
    }

    @Override
    public boolean canUse(ChildView child) {
        return !child.deleted() && child.memberId().equals(memberId);
    }
}
