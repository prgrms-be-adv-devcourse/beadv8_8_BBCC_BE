package com.bbcc.kidly.boundedcontext.recommend.app.port.recommended;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * 더보기 목록에서 뺄 상품의 ID를 읽는 나가는 연결의 요청서다.
 * 기간은 호출하는 쪽이 시각으로 넘기고, 기간의 시작 시각은 기간에 포함한다.
 */
public interface RecommendExcludedProducts {

    /**
     * 회원이 cartAddedSince부터 장바구니에 담고 아직 빼지 않은 상품과, 자녀가 구매확정한 상품의 ID를 읽는다.
     * 장바구니 상품은 어느 자녀로 담았든 회원 단위로 읽고, 구매확정 상품은 기간 없이 그 자녀의 것만 읽는다.
     */
    Set<Long> findIds(Long memberId, Long childProfileId, LocalDateTime cartAddedSince);
}
