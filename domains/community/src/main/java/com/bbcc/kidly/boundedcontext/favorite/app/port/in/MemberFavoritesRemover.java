package com.bbcc.kidly.boundedcontext.favorite.app.port.in;

/**
 * 회원의 찜을 모두 지우고 지운 만큼 상품별 찜 수를 줄인다(회원 탈퇴). 부르는 쪽 트랜잭션에 합류한다.
 * 지울 찜이 없으면 아무것도 바꾸지 않는다(같은 회원의 탈퇴가 다시 와도 1회분).
 */
public interface MemberFavoritesRemover {

    /**
     * @return 지운 찜 수
     */
    int removeAll(Long memberId);
}
