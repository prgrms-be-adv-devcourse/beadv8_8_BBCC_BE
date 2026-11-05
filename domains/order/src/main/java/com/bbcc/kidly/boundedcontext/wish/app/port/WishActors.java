package com.bbcc.kidly.boundedcontext.wish.app.port;

import java.util.Optional;

/**
 * 로그인한 사람을 조르기 호출 주체로 바꾼다(#640). 구현은 out. 부모는 회원 로그인, 자녀는 자녀 로그인(#906)이다.
 */
public interface WishActors {

    /**
     * @param memberId 로그인한 부모 회원 ID
     */
    WishActor parent(Long memberId);

    /**
     * 자녀 로그인을 그 자녀 프로필의 호출 주체로 바꾼다(#906). 주문이 받아 둔 계정 복사본으로 프로필을 찾는다(회원을 동기로 묻지 않는다).
     *
     * @param accountId 로그인한 자녀 계정 ID
     * @param guardianId 자녀 토큰의 보호자 회원 ID
     * @return 계정 복사본이 없거나 · 삭제됐거나 · 보호자가 다르면 빈 값(부르는 쪽이 403)
     */
    Optional<WishActor> child(Long accountId, Long guardianId);
}
