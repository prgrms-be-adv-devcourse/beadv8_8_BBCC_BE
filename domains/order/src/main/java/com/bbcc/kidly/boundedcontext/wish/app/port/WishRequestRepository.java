package com.bbcc.kidly.boundedcontext.wish.app.port;

import com.bbcc.kidly.boundedcontext.wish.domain.PrincipalType;
import com.bbcc.kidly.boundedcontext.wish.domain.WishRequest;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;

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
     * 이 보낸 사람의 이 상태 조르기(#1001 삭제 정리: 진행 중 조르기 철회). ID 순.
     */
    List<WishRequest> findAllByRequestedByTypeAndRequestedByIdAndStatusOrderByIdAsc(PrincipalType type,
        Long requestedById, WishRequest.Status status);

    /**
     * 위와 같은 조건의 조르기 ID만(조르기를 올리지 않는다, #1207). 예약을 먼저 잠근 뒤 조르기를 읽으려고 쓴다. ID 순.
     */
    List<Long> findIdsByRequester(PrincipalType type, Long requestedById, WishRequest.Status status);

    /**
     * 이 상태의 조르기에 이 칸 중 하나라도 들어 있는지(진행 중 칸 중복 금지, BEG-02).
     */
    boolean existsByStatusAndLinesCartItemIdIn(WishRequest.Status status, Collection<Long> cartItemIds);

    /**
     * 기한이 지난(sentBefore 이전 포함에 보낸) 이 상태의 조르기 ID. afterId보다 큰 ID만 오름차순으로 limit건(주기 작업의 ID 커서, #1008).
     */
    List<Long> findOverdueRequestIds(WishRequest.Status status, LocalDateTime sentBefore, Long afterId, Limit limit);

    /**
     * 이 상태의 조르기를 afterId보다 큰 ID부터 오름차순 limit건(예약 기한 다시 맞추기의 ID 커서, #1008).
     */
    List<WishRequest> findAllByStatusAndIdGreaterThanOrderByIdAsc(WishRequest.Status status, Long afterId,
        Limit limit);

    /**
     * 결제 완료 · 거절 · 철회를 바로 DB에 써서, 같은 조르기를 동시에 처리하는 쪽이 장바구니를 건드리기 전에 버전 충돌로 지게 한다.
     */
    void flush();
}
