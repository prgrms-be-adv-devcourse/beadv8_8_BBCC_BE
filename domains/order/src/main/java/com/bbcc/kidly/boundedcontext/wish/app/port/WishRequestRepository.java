package com.bbcc.kidly.boundedcontext.wish.app.port;

import com.bbcc.kidly.boundedcontext.wish.domain.PrincipalType;
import com.bbcc.kidly.boundedcontext.wish.domain.WishRequest;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 조르기 저장소(#701 · #999). 구현은 out/persistence (Spring Data가 만든다).
 */
public interface WishRequestRepository {

    WishRequest save(WishRequest request);

    Optional<WishRequest> findById(Long id);

    /**
     * 이 보낸 사람들의 조르기를 최근부터(줄 포함).
     */
    List<WishRequest> findAllByRequestedByTypeAndRequestedByIdInOrderByIdDesc(PrincipalType type,
        Collection<Long> requestedByIds);

    /**
     * 이 상태의 조르기에 이 칸 중 하나라도 들어 있는지(진행 중 칸 중복 금지, BEG-02).
     */
    boolean existsByStatusAndLinesCartItemIdIn(WishRequest.Status status, Collection<Long> cartItemIds);

    /**
     * 결제 완료 · 거절 · 철회를 바로 DB에 써서, 같은 조르기를 동시에 처리하는 쪽이 장바구니를 건드리기 전에 버전 충돌로 지게 한다.
     */
    void flush();
}
