package com.bbcc.kidly.boundedcontext.wish.app;

import com.bbcc.kidly.boundedcontext.wish.app.port.WishCart;
import com.bbcc.kidly.boundedcontext.wish.app.port.WishCatalog;
import com.bbcc.kidly.boundedcontext.wish.app.port.WishEvents;
import com.bbcc.kidly.boundedcontext.wish.app.port.WishRequestRepository;
import com.bbcc.kidly.boundedcontext.wish.domain.WishRequest;
import com.bbcc.kidly.shared.order.event.WishExpiredEvent;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 조르기 기한 만료(#1008, BEG-05). 주기 작업(WishExpiryScheduler)이 기한 지난 대기 조르기를 찾아 하나씩 만료한다(대상 하나 = 트랜잭션 하나).
 * 만료하면 장바구니 예약을 풀고(칸은 남아 다시 조를 수 있다) wish.expired를 같은 트랜잭션에서 낸다.
 * 결제 정리로 예약 칸이 이미 빠졌거나 칸이 남는 일부 수량 결제라도 장바구니가 예약에 결제 정리 표시를 남겼으면(hasPurchase), 구매 사실이
 * 아직 오지 않은 것이므로(결제 반영 중) 만료하지 않는다(곧 결제 완료가 된다. 거절 · 철회 409와 같은 기준).
 * 만료 작업은 기한 + 유예(grace)가 지난 것만 만료한다: 기한 전에 만든 주문의 결제 완료가 늦게 와도 반영되게 한다(WishExpiry.isExpirable).
 * 결제 완료와 동시에 오면 먼저 저장한 쪽이 이긴다(@Version): 만료가 이기면 예약이 풀린 뒤라 그 결제는 조르기와 이어지지 않는다(구매 사실에
 * 예약 ID가 없어 산 것에 남지 않고 상태 · 이벤트 그대로. 예약 ID가 실린 구매 사실이 늦게 오면 WishPurchaseService가 산 것만 남긴다).
 * 확인한 뒤 결제 정리가 커밋되면 만료의 예약 해제가 장바구니 예약 버전(cart_reservation.version)으로 지고
 * 이 건만 롤백된다(주기 작업은 다음 건을 계속 처리하고, 다음 실행에서 결제 반영 중으로 건너뛴다). 꺼져 있으면(kidly.order.wish.expiry.enabled=false) 아무것도 만료하지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "kidly.order.wish.enabled", havingValue = "true", matchIfMissing = true)
public class WishExpiryService {

    private final WishRequestRepository wishRequestRepository;
    private final WishCatalog wishCatalog;
    private final WishCart wishCart;
    private final WishEvents wishEvents;
    private final WishExpiry wishExpiry;

    /**
     * now 기준 기한 + 유예가 지난 대기 조르기 ID(afterId 다음부터 오름차순 limit건). 꺼져 있으면 빈 목록.
     */
    @Transactional(readOnly = true)
    public List<Long> findOverdueRequestIds(LocalDateTime now, Long afterId, int limit) {
        if (!wishExpiry.enabled()) {
            return List.of();
        }
        return wishRequestRepository.findOverdueRequestIds(WishRequest.Status.REQUESTED,
            wishExpiry.sentBeforeExpirable(now), afterId, Limit.of(limit));
    }

    /**
     * 아직 대기이고 now 기준 기한 + 유예가 지났으면 만료한다. 그 사이 결제 · 거절 · 철회됐거나 기한이 늘었으면(설정 변경) · 결제 반영 중이면
     * 하지 않는다.
     *
     * @return 이번에 만료했으면 true
     */
    @Transactional
    public boolean expire(Long requestId, LocalDateTime now) {
        WishRequest request = wishRequestRepository.findById(requestId).orElse(null);
        if (request == null || !wishExpiry.isExpirable(request, now)) {
            return false;
        }
        if (wishCart.reservedCartItemIds(request.reservationId()).size() < request.getLines().size()
            || wishCart.hasPurchase(request.reservationId())) {
            // 결제 정리가 예약 칸을 정리했는데(빠짐 · 일부 차감) 아직 구매 사실이 오지 않았다(Kafka 지연): 곧 결제 완료가 되므로 만료하지 않는다
            log.debug("결제 반영 중이라 만료하지 않습니다: requestId={}", request.getId());
            return false;
        }
        request.expire(now);
        wishRequestRepository.flush();
        wishCart.release(request.reservationId());
        Long guardianId = wishCatalog.findChild(request.getChildId()).map(WishCatalog.Child::memberId).orElse(null);
        wishEvents.publish(new WishExpiredEvent(UUID.randomUUID(), now, new WishExpiredEvent.Data(
            request.getId(), request.getChildId(), guardianId, request.getLines().stream()
                .map(line -> new WishExpiredEvent.Item(line.getProductId(), line.getOptionId(), line.getQuantity()))
                .toList())));
        log.info("조르기 만료: requestId={}", request.getId());
        return true;
    }
}
