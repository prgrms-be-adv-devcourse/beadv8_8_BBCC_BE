package com.bbcc.kidly.boundedcontext.wish.app.port;

import com.bbcc.kidly.boundedcontext.wish.domain.WishItem.AddedBy;

/**
 * 조르기를 부르는 사람(#640). 바깥(로그인)에서 오는 연결이라 포트로 둔다. 구현은 out이고, 회원 로그인은 부모({@link WishActors#parent}),
 * 자녀 로그인은 그 자녀 프로필({@link WishActors#child}, #906)이다.
 */
public interface WishActor {

    AddedBy type();

    /**
     * 부모면 회원 ID, 자녀면 자녀 프로필 ID.
     */
    Long id();

    /**
     * 이 자녀(주문이 받아 둔 자녀 복사본)의 공용 장바구니를 쓸 수 있는지. 삭제된 자녀는 누구도 쓸 수 없다.
     */
    boolean canUse(WishCatalog.Child child);
}
