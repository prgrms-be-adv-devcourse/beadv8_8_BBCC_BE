package com.bbcc.kidly.boundedcontext.wish.app;

import com.bbcc.kidly.boundedcontext.wish.app.port.WishCart;
import com.bbcc.kidly.boundedcontext.wish.app.port.WishRequestRepository;
import com.bbcc.kidly.boundedcontext.wish.domain.WishRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 진행 중(REQUESTED) 조르기의 장바구니 예약 결제 가능 기한을 지금 만료 설정으로 다시 맞춘다(#1008). 기한은 저장하지 않고
 * 보낸 시각 + ttl로 계산하므로({@link WishExpiry}), ttl이 바뀌거나 만료가 꺼지면(→ null) 장바구니 쪽 사본만 따라 바꾼다.
 * 배치 하나 = 트랜잭션 하나(ID 커서). 값이 같은 예약은 장바구니가 쓰지 않는다.
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "kidly.order.wish.enabled", havingValue = "true", matchIfMissing = true)
public class WishReservationValidUntilService {

    private final WishRequestRepository wishRequestRepository;
    private final WishCart wishCart;
    private final WishExpiry wishExpiry;

    /**
     * afterId 다음 진행 중 조르기 limit건의 예약 기한을 맞춘다.
     *
     * @return 읽은 조르기 수 · 마지막 ID · 실제로 바뀐 예약 수
     */
    @Transactional
    public Batch syncBatch(Long afterId, int limit) {
        List<WishRequest> requests = wishRequestRepository.findAllByStatusAndIdGreaterThanOrderByIdAsc(
            WishRequest.Status.REQUESTED, afterId, Limit.of(limit));
        if (requests.isEmpty()) {
            return new Batch(0, afterId, 0);
        }
        int changed = wishCart.updateValidUntil(requests.stream()
            .map(request -> new WishCart.ValidUntil(request.reservationId(), wishExpiry.reservationValidUntil(request)))
            .toList());
        return new Batch(requests.size(), requests.getLast().getId(), changed);
    }

    /**
     * afterId 다음 진행 중 조르기 ID limit건(배치가 잠금 실패(교착 등)로 졌을 때 한 건씩 다시 맞추려고 쓴다, #1207).
     */
    @Transactional(readOnly = true)
    public List<Long> requestIds(Long afterId, int limit) {
        return wishRequestRepository.findAllByStatusAndIdGreaterThanOrderByIdAsc(WishRequest.Status.REQUESTED, afterId,
            Limit.of(limit)).stream().map(WishRequest::getId).toList();
    }

    /**
     * 조르기 하나의 예약 기한을 맞춘다(자기 트랜잭션). 그 사이 처리돼 대기가 아니면 하지 않는다.
     *
     * @return 실제로 바뀐 예약 수(0 또는 1)
     */
    @Transactional
    public int syncOne(Long requestId) {
        return wishRequestRepository.findById(requestId)
            .filter(request -> request.getStatus() == WishRequest.Status.REQUESTED)
            .map(request -> wishCart.updateValidUntil(List.of(
                new WishCart.ValidUntil(request.reservationId(), wishExpiry.reservationValidUntil(request)))))
            .orElse(0);
    }

    /**
     * @param read 이번에 읽은 조르기 수(limit보다 적으면 끝)
     * @param lastId 이번에 읽은 마지막 조르기 ID(다음 커서)
     * @param changed 기한이 실제로 바뀐 예약 수
     */
    public record Batch(int read, Long lastId, int changed) {
    }
}
