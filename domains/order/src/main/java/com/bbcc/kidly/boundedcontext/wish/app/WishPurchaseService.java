package com.bbcc.kidly.boundedcontext.wish.app;

import com.bbcc.kidly.boundedcontext.wish.app.port.WishCart;
import com.bbcc.kidly.boundedcontext.wish.app.port.WishCatalog;
import com.bbcc.kidly.boundedcontext.wish.app.port.WishEvents;
import com.bbcc.kidly.boundedcontext.wish.app.port.WishPurchaseRepository;
import com.bbcc.kidly.boundedcontext.wish.app.port.WishRequestRepository;
import com.bbcc.kidly.boundedcontext.wish.domain.PrincipalType;
import com.bbcc.kidly.boundedcontext.wish.domain.WishPurchase;
import com.bbcc.kidly.boundedcontext.wish.domain.WishRequest;
import com.bbcc.kidly.shared.order.event.WishPaidEvent;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 조르기 결제 완료(#999, 승인 = 결제). 장바구니가 결제 완료 정리로 칸을 지우며 낸 구매 사실(cart.item-purchased, 예약 ID ·
 * 주문 ID · 주문상품 ID)을 받아, 조르기 예약 칸이면 결제 완료로 바꾼다. 결제 정리 트랜잭션과는 따로(Kafka 수신, 참모 결정 ①) 자기 트랜잭션에서
 * 처리하므로, 여기가 실패해도 결제 완료는 되돌아가지 않는다(실패는 수신 쪽이 재시도 → DLT).
 * <ul>
 *   <li>처음 받은 칸: 결제 완료 + 결제한 사람(이벤트의 결제 회원) 기록, 남은 칸 예약 해제, 산 칸 기록, wish.paid 1번</li>
 *   <li>같은 주문의 다른 칸 · 같은 메시지 다시 받기 · 순서가 바뀐 칸: 산 칸만 (없으면) 더하고 이벤트는 다시 내지 않는다(1회분)</li>
 *   <li>조르기의 예약이 아님 · 없는 조르기 · 그 조르기의 칸이 아님: 아무것도 하지 않는다</li>
 *   <li>조르기 예약 칸인데 주문 ID · 결제 회원이 없음: 건너뛰고 WARN 한 줄(조르기 ID · 칸 ID)</li>
 *   <li>예약 ID가 실린 구매 사실이 이미 거절 · 철회 · 만료(#1008)된 조르기에 오면(다시 처리 등): 상태는 바꾸지 않고 이벤트도 내지 않지만,
 *       산 사실(조르기로 산 것)은 남기고 WARN 한 줄(조르기 ID · 주문 ID)을 남긴다. 보통은 거절 · 철회 · 만료가 예약을 먼저 풀어
 *       결제 정리의 구매 사실에 예약 ID가 없으므로 그 결제는 조르기와 이어지지 않는다(산 것에 남지 않음)</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "kidly.order.wish.enabled", havingValue = "true", matchIfMissing = true)
public class WishPurchaseService {

    private final WishRequestRepository wishRequestRepository;
    private final WishPurchaseRepository wishPurchaseRepository;
    private final WishCatalog wishCatalog;
    private final WishCart wishCart;
    private final WishEvents wishEvents;

    /**
     * 결제로 장바구니에서 산 만큼 빠진 칸 하나(일부 수량만 사면 칸은 남는다). 일부만 사도 그 조르기는 결제 완료이고, 예약 전체를 풀어
     * 남은 칸은 자녀 장바구니에 예약 없이 남는다.
     *
     * @param orderItemId 결제된 주문상품 ID. 이벤트에 없으면 null
     * @param payerMemberId 칸의 결제 회원(실제 결제자와 다를 수 있음 — 보호자 2명 등). wish.paid의 결제한 사람이 된다
     * @param quantity 이번에 산 수량(칸에 남은 수량 아님)
     * @param reservationId 빠질 때 칸의 예약 ID. 예약 칸이 아니었으면 null
     */
    public record PurchasedLine(Long cartItemId, Long orderId, Long orderItemId, Long payerMemberId, Long optionId,
        int quantity, String reservationId) {
    }

    /**
     * 늘 새 트랜잭션에서 처리한다: 결제 정리 트랜잭션이 커밋된 뒤(Spring 되돌리기 경로) 불려도 그 트랜잭션에 끼지 않는다.
     *
     * @return 이번에 결제 완료가 됐으면 true
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean onPurchased(PurchasedLine line) {
        Optional<WishRequest> found = WishRequest.idOfReservation(line.reservationId())
            .flatMap(wishRequestRepository::findById)
            .filter(request -> request.containsCartItem(line.cartItemId()));
        if (found.isEmpty()) {
            return false;
        }
        if (line.orderId() == null || line.payerMemberId() == null) {
            log.warn("조르기 예약 칸의 구매 사실에 주문 · 결제 회원이 없어 건너뜁니다: requestId={} cartItemId={}",
                found.get().getId(), line.cartItemId());
            return false;
        }
        WishRequest request = found.get();
        if (wishPurchaseRepository.existsByRequestIdAndCartItemId(request.getId(), line.cartItemId())) {
            return false;
        }
        if (request.getStatus() != WishRequest.Status.REQUESTED && request.getStatus() != WishRequest.Status.PAID) {
            wishPurchaseRepository.save(WishPurchase.of(request.getId(), line.orderId(), line.orderItemId(),
                line.cartItemId(), line.optionId(), line.quantity()));
            log.warn("처리된 조르기의 칸이 결제됐습니다(상태는 그대로): requestId={} orderId={}", request.getId(),
                line.orderId());
            return false;
        }
        boolean paidNow = request.pay(PrincipalType.MEMBER, line.payerMemberId(), LocalDateTime.now());
        wishPurchaseRepository.save(WishPurchase.of(request.getId(), line.orderId(), line.orderItemId(),
            line.cartItemId(), line.optionId(), line.quantity()));
        if (!paidNow) {
            return false;
        }
        wishRequestRepository.flush();
        wishCart.release(request.reservationId());
        Long guardianId = wishCatalog.findChild(request.getChildId()).map(WishCatalog.Child::memberId).orElse(null);
        wishEvents.publish(new WishPaidEvent(UUID.randomUUID(), LocalDateTime.now(), new WishPaidEvent.Data(
            request.getId(), request.getChildId(), guardianId, request.getLines().stream()
                .map(item -> new WishPaidEvent.Item(item.getProductId(), item.getOptionId(), item.getQuantity()))
                .toList(),
            PrincipalType.MEMBER.name(), line.payerMemberId(), line.orderId())));
        log.info("조르기 결제 완료: requestId={} orderId={}", request.getId(), line.orderId());
        return true;
    }
}
