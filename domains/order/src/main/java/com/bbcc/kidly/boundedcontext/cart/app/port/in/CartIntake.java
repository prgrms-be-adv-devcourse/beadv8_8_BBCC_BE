package com.bbcc.kidly.boundedcontext.cart.app.port.in;

/**
 * 다른 컨텍스트(조르기)가 회원의 장바구니에 담는다(#701 · #641). 구현은 장바구니 app이고 부르는 쪽 트랜잭션에 합류한다.
 * 장바구니 담기와 같은 길이다: 같은 검증 · 같은 줄 합치기 · 성공하면 cart.item-added.
 */
public interface CartIntake {

    /**
     * 실패하면 장바구니 담기와 같은 업무 예외가 그대로 나간다.
     *
     * @param childId 자녀를 고르지 않으면 null
     */
    void add(Long memberId, Long optionId, Long childId, int quantity);
}
