package com.bbcc.kidly.boundedcontext.wish.app;

import com.bbcc.kidly.boundedcontext.wish.app.port.WishCart;
import com.bbcc.kidly.boundedcontext.wish.app.port.WishCatalog;
import com.bbcc.kidly.boundedcontext.wish.app.port.WishEvents;
import com.bbcc.kidly.boundedcontext.wish.app.port.WishRequestRepository;
import com.bbcc.kidly.boundedcontext.wish.domain.PrincipalType;
import com.bbcc.kidly.boundedcontext.wish.domain.WishRequest;
import com.bbcc.kidly.boundedcontext.wish.domain.WishRequest.CancelReason;
import com.bbcc.kidly.shared.order.event.WishCancelledEvent;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 자녀 · 자녀 계정 삭제 때 진행 중 조르기 철회(#1001, CHILD-05 · ACCOUNT-03). 그 자녀 프로필이 보낸 대기 조르기를 원인 CHILD_DELETED ·
 * ACCOUNT_DELETED로 철회하고 예약을 풀고(칸은 장바구니가 이미 지웠다) wish.cancelled(+ cancelReason)를 낸다.
 * 부르는 쪽(삭제 수신) 트랜잭션에 합류한다. 대기 조르기만 대상이라 같은 삭제가 여러 번 · 다른 삭제가 먼저 와도 한 번만 철회된다
 * (먼저 온 원인이 남는다).
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
@ConditionalOnProperty(name = "kidly.order.wish.enabled", havingValue = "true", matchIfMissing = true)
public class WishDeletionService {

    private final WishRequestRepository wishRequestRepository;
    private final WishCatalog wishCatalog;
    private final WishCart wishCart;
    private final WishEvents wishEvents;

    /**
     * 자녀 프로필 삭제: 원인 CHILD_DELETED.
     *
     * @return 이번에 철회한 조르기 수
     */
    public int cancelForChildDeleted(Long childProfileId) {
        return cancelRequestsOf(childProfileId, CancelReason.CHILD_DELETED);
    }

    /**
     * 자녀 계정 삭제: 원인 ACCOUNT_DELETED.
     *
     * @return 이번에 철회한 조르기 수
     */
    public int cancelForAccountDeleted(Long childProfileId) {
        return cancelRequestsOf(childProfileId, CancelReason.ACCOUNT_DELETED);
    }

    private int cancelRequestsOf(Long childProfileId, CancelReason reason) {
        List<WishRequest> requests = wishRequestRepository
            .findAllByRequestedByTypeAndRequestedByIdAndStatusOrderByIdAsc(PrincipalType.CHILD, childProfileId,
                WishRequest.Status.REQUESTED);
        LocalDateTime now = LocalDateTime.now();
        for (WishRequest request : requests) {
            request.cancel(reason, now);
            wishRequestRepository.flush();
            wishCart.release(request.reservationId());
            Long guardianId = wishCatalog.findChild(request.getChildId()).map(WishCatalog.Child::memberId)
                .orElse(null);
            wishEvents.publish(new WishCancelledEvent(UUID.randomUUID(), now, new WishCancelledEvent.Data(
                request.getId(), request.getChildId(), guardianId, request.getLines().stream()
                    .map(line -> new WishCancelledEvent.Item(line.getProductId(), line.getOptionId(),
                        line.getQuantity()))
                    .toList(),
                reason.name())));
            log.info("조르기 철회(삭제): requestId={} reason={}", request.getId(), reason);
        }
        return requests.size();
    }
}
