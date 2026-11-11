package com.bbcc.kidly.boundedcontext.wish.out.actor;

import com.bbcc.kidly.boundedcontext.wish.app.port.WishActor;
import com.bbcc.kidly.boundedcontext.wish.domain.PrincipalType;

/**
 * 로그인한 회원(보호자, #640 · #999). 자기 자녀가 보낸 조르기의 승인자다(WishApprovers).
 */
public record ParentWishActor(Long memberId) implements WishActor {

    @Override
    public PrincipalType type() {
        return PrincipalType.MEMBER;
    }

    @Override
    public Long id() {
        return memberId;
    }

    @Override
    public Long familyId() {
        return memberId;
    }
}
