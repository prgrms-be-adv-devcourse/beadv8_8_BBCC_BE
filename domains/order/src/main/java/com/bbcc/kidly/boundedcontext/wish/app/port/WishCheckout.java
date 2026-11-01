package com.bbcc.kidly.boundedcontext.wish.app.port;

import java.util.List;

/**
 * 조르기 줄을 구매로 넘기는 곳(#640). 부모 장바구니로 옮기기 · 바로 주문하기 중 팀이 정한 하나로 구현한다(지금은 구현 없음).
 * 구현은 다른 컨텍스트의 api(장바구니 · 주문)를 부르는 out에 둔다.
 */
public interface WishCheckout {

    /**
     * @param memberId 구매하는 부모 회원 ID
     * @param wishItemIds 넘길 조르기 줄(같은 자녀)
     */
    void checkout(Long memberId, List<Long> wishItemIds);
}
