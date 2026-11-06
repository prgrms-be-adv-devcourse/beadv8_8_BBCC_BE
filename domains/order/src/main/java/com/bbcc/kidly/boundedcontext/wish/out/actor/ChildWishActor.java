package com.bbcc.kidly.boundedcontext.wish.out.actor;

import com.bbcc.kidly.boundedcontext.wish.app.port.WishActor;
import com.bbcc.kidly.boundedcontext.wish.app.port.WishCatalog;
import com.bbcc.kidly.boundedcontext.wish.domain.WishItem.AddedBy;

/**
 * 로그인한 자녀(#906). 자기 프로필(계정 복사본으로 찾은 프로필)의 공용 장바구니만 쓴다: 담기 · 빼기(정책상 자기가 담은 줄) · 조회 ·
 * 조르기 보내기 · 내 조르기 조회. 승인 · 거절은 정책(WishPolicy.canDecide)상 보호자만이라 자녀면 403이다.
 * 담은 사람 · 보낸 사람은 자녀(종류 CHILD · ID = 자녀 프로필 ID)로 남는다.
 *
 * @param childProfileId 자녀 프로필 ID
 * @param guardianId 자녀 토큰과 계정 복사본이 같다고 확인한 보호자 회원 ID
 */
public record ChildWishActor(Long childProfileId, Long guardianId) implements WishActor {

    @Override
    public AddedBy type() {
        return AddedBy.CHILD;
    }

    @Override
    public Long id() {
        return childProfileId;
    }

    /**
     * 자기 프로필이고 삭제되지 않았고 보호자가 같은 자녀만 쓴다.
     */
    @Override
    public boolean canUse(WishCatalog.Child child) {
        return !child.deleted() && child.childId().equals(childProfileId) && child.memberId().equals(guardianId);
    }
}
