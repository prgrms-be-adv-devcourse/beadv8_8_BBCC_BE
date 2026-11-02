package com.bbcc.kidly.boundedcontext.wish.app.port;

import com.bbcc.kidly.boundedcontext.wish.domain.WishRequest;
import java.util.List;
import java.util.Optional;

/**
 * 조르기 저장소(#701). 구현은 out/persistence (Spring Data가 만든다).
 */
public interface WishRequestRepository {

    WishRequest save(WishRequest request);

    Optional<WishRequest> findById(Long id);

    /**
     * 최근 조르기부터(줄 포함).
     */
    List<WishRequest> findAllByChildIdOrderByIdDesc(Long childId);

    /**
     * 그 자녀의 조르기 중 상태 · 구매 방법이 같고 주문 결과가 아직 없는 것(바로 결제의 결제 결과를 기다리는 조르기).
     */
    List<WishRequest> findAllByChildIdAndStatusAndCheckoutAndOrderResultIsNull(
        Long childId, WishRequest.Status status, WishRequest.Checkout checkout);

    /**
     * 승인 · 거절을 바로 DB에 써서, 같은 조르기를 동시에 처리하는 쪽이 주문 · 장바구니를 건드리기 전에 버전 충돌로 지게 한다.
     */
    void flush();
}
