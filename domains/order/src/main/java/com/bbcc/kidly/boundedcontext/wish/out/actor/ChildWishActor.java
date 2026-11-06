package com.bbcc.kidly.boundedcontext.wish.out.actor;

import com.bbcc.kidly.boundedcontext.wish.app.port.WishActor;
import com.bbcc.kidly.boundedcontext.wish.domain.PrincipalType;

/**
 * 로그인한 자녀(#906 · #999). 자기 장바구니 칸으로 조르기를 보내고, 자기가 보낸 조르기만 보고 철회한다.
 * 보낸 사람은 자녀(종류 CHILD · ID = 자녀 프로필 ID)로 남는다.
 *
 * @param childProfileId 자녀 프로필 ID
 * @param guardianId 자녀 토큰과 계정 복사본이 같다고 확인한 보호자 회원 ID
 */
public record ChildWishActor(Long childProfileId, Long guardianId) implements WishActor {

    @Override
    public PrincipalType type() {
        return PrincipalType.CHILD;
    }

    @Override
    public Long id() {
        return childProfileId;
    }

    @Override
    public Long familyId() {
        return guardianId;
    }
}
