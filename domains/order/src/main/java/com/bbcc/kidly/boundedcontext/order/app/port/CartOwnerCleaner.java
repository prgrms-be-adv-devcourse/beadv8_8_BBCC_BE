package com.bbcc.kidly.boundedcontext.order.app.port;

/**
 * 삭제 · 탈퇴된 주인의 장바구니를 비운다(#1001). 구현은 out의 장바구니 컨텍스트 어댑터이고 부르는 쪽 트랜잭션에 합류한다.
 * 정리라 장바구니 이벤트(cart.item-removed)는 나가지 않는다.
 */
public interface CartOwnerCleaner {

    /**
     * 장바구니 주인 종류.
     */
    enum OwnerType {
        /**
         * 회원 장바구니(주인 ID = 회원 ID).
         */
        MEMBER,
        /**
         * 자녀 프로필 장바구니(주인 ID = 자녀 프로필 ID).
         */
        CHILD
    }

    /**
     * @return 지운 칸 수
     */
    int removeAll(OwnerType ownerType, Long ownerId);
}
