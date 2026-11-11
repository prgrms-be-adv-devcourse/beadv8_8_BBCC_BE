package com.bbcc.kidly.boundedcontext.wish.app.port;

import com.bbcc.kidly.boundedcontext.wish.domain.WishPurchase;
import java.util.Collection;
import java.util.List;

/**
 * 조르기로 산 것 저장소(#999). 구현은 out/persistence (Spring Data가 만든다).
 */
public interface WishPurchaseRepository {

    WishPurchase save(WishPurchase purchase);

    boolean existsByRequestIdAndCartItemId(Long requestId, Long cartItemId);

    /**
     * 이 조르기들의 산 것(먼저 산 것부터).
     */
    List<WishPurchase> findAllByRequestIdInOrderByIdAsc(Collection<Long> requestIds);
}
