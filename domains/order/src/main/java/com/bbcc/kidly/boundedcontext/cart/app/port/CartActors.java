package com.bbcc.kidly.boundedcontext.cart.app.port;

import com.bbcc.kidly.boundedcontext.cart.domain.CartItem.OwnerType;
import java.util.Optional;

/**
 * 로그인한 사람을 장바구니 주인으로 바꾼다(#998). 회원 로그인은 그 회원, 자녀 로그인은 그 자녀 프로필(#906과 같은 규칙)이다.
 * 구현은 out(주문이 받아 둔 자녀 계정 복사본으로 프로필을 찾는다).
 */
public interface CartActors {

    Actor member(Long memberId);

    /**
     * @param accountId 로그인한 자녀 계정 ID
     * @param guardianId 자녀 토큰의 보호자 회원 ID
     * @return 계정 복사본이 없거나 · 삭제됐거나 · 보호자가 다르면 빈 값(부르는 쪽이 403)
     */
    Optional<Actor> child(Long accountId, Long guardianId);

    /**
     * 장바구니 주인.
     *
     * @param id 회원이면 회원 ID, 자녀면 자녀 프로필 ID
     * @param memberId 결제할 보호자 회원 ID(회원이면 id와 같다)
     */
    record Actor(OwnerType type, Long id, Long memberId) {
    }
}
