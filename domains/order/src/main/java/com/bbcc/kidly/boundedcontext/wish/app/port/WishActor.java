package com.bbcc.kidly.boundedcontext.wish.app.port;

import com.bbcc.kidly.boundedcontext.wish.domain.PrincipalType;

/**
 * 조르기를 부르는 사람(#640 · #999). 바깥(로그인)에서 오는 연결이라 포트로 둔다. 구현은 out이고, 회원 로그인은 회원
 * ({@link WishActors#parent}), 자녀 로그인은 그 자녀 프로필({@link WishActors#child}, #906)이다.
 */
public interface WishActor {

    PrincipalType type();

    /**
     * 회원이면 회원 ID, 자녀면 자녀 프로필 ID.
     */
    Long id();

    /**
     * 가족(결제 회원): 회원이면 자기, 자녀면 토큰과 계정 복사본이 같다고 확인한 보호자 회원 ID. 같은 가족의 남의 조르기는 403,
     * 다른 가족이면 없는 조르기(404)로 보는 데 쓴다.
     */
    Long familyId();
}
