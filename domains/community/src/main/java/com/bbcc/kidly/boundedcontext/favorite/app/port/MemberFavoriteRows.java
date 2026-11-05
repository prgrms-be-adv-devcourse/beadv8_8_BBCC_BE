package com.bbcc.kidly.boundedcontext.favorite.app.port;

import java.util.List;

/**
 * 회원 단위 찜 행 다루기(회원 탈퇴). 구현은 out/persistence(Spring Data가 만든다).
 */
public interface MemberFavoriteRows {

    /**
     * 그 회원의 찜을 한 문장으로 모두 지우고, 실제로 지운 행의 상품 ID를 돌려준다(delete … returning).
     * 같은 회원의 찜 해제와 겹쳐도 한쪽만 지우고, 지운 뒤 새로 들어온 찜은 지우지도 세지도 않아 찜 수와 어긋나지 않는다.
     *
     * @return 지운 찜의 상품 ID(지운 행마다 하나)
     */
    List<Long> deleteAllByMember(Long memberId);
}
