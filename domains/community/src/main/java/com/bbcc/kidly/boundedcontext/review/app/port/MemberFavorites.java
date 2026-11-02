package com.bbcc.kidly.boundedcontext.review.app.port;

/**
 * 회원 탈퇴 때 그 회원의 찜을 정리한다(찜 삭제 · 찜 수 차감은 찜 쪽 일). 구현은 out 어댑터.
 */
public interface MemberFavorites {

    /**
     * @return 지운 찜 수
     */
    int removeAll(Long memberId);
}
