package com.bbcc.kidly.boundedcontext.wish.app.port;

/**
 * 로그인한 사람을 조르기 호출 주체로 바꾼다(#640). 구현은 out(지금은 부모만). 자녀 계정 인증이 준비되면 자녀 쪽을 여기에 더한다.
 */
public interface WishActors {

    /**
     * @param memberId 로그인한 부모 회원 ID
     */
    WishActor parent(Long memberId);
}
