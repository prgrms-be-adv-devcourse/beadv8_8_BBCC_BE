package com.bbcc.kidly.boundedcontext.wish.app.port;

/**
 * 승인한 조르기 줄을 보호자 개인 장바구니에 담는다(#701). 구현은 out의 장바구니 컨텍스트 어댑터이고 부르는 쪽 트랜잭션에 합류한다.
 * 장바구니 담기와 같은 길이라 실패하면 장바구니의 업무 예외가 그대로 나간다(조르기 승인도 함께 롤백).
 */
public interface WishParentCart {

    /**
     * @param childId 장바구니 줄의 자녀(조른 자녀)
     */
    void put(Long memberId, Long optionId, Long childId, int quantity);
}
