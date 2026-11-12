package com.bbcc.kidly.boundedcontext.order.app.order;

import com.bbcc.kidly.boundedcontext.order.app.port.OrderRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.SellerReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.domain.order.Order;
import com.bbcc.kidly.boundedcontext.order.domain.order.OrderItem;
import com.bbcc.kidly.shared.seller.domain.SellerStatus;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;

/**
 * 판매자가 판매할 수 없게 되면(#1121, seller.changed) 그 판매자의 배송 전 주문을 정리한다. 판단은 판매자 복사본의 지금 상태가
 * ACTIVE인지 하나뿐이다(그 밖의 값은 모두 판매 불가: 판매자 상태 값이 늘어도 주문은 고치지 않는다). 복사본이 없으면 아무것도 하지 않는다.
 * 복사본은 늦게 발생한 변경만 반영하므로 순서가 바뀐 메시지에도 최신 상태로 판단한다. 새 주문을 막는 것은 주문 일이 아니다(상품 판매중지).
 * <ul>
 *   <li>대상: 그 판매자 상품의 주문됨 주문상품이 든 결제대기 · 결제완료 주문. ID 커서로 {@value #BATCH_SIZE}건씩 읽는다</li>
 *   <li>한 주문 = 한 트랜잭션({@link OrderService#cancelForUnavailableSeller}): 판매자 한 명의 주문이 많을 수 있어 한 건의 실패가
 *       전체를 되돌리지 않게 한다. 실패한 주문이 있으면 끝에 예외를 던져 수신 쪽이 재시도한다(이미 정리한 것은 건너뛰어 결과 · 이벤트는
 *       1회분)</li>
 * </ul>
 * 트랜잭션을 열지 않는다(복사본 반영 트랜잭션이 커밋된 뒤 부른다).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SellerUnavailableCleanupService {

    static final int BATCH_SIZE = 100;
    private static final List<Order.Status> TARGET_STATUSES = List.of(Order.Status.PENDING_PAYMENT, Order.Status.PAID);

    private final SellerReplicaRepository sellerReplicaRepository;
    private final OrderRepository orderRepository;
    private final OrderService orderService;

    /**
     * @return 정리를 시도한 주문 수(판매 가능 · 복사본 없음이면 0)
     * @throws IllegalStateException 정리하지 못한 주문이 있으면(수신 쪽 재시도)
     */
    public int cleanUpIfUnavailable(Long sellerId) {
        if (sellerId == null || !isUnavailable(sellerId)) {
            return 0;
        }
        int targets = 0;
        int failed = 0;
        Long afterId = 0L;
        while (true) {
            List<Long> orderIds = orderRepository.findOrderIdsWithSellerItems(sellerId, OrderItem.Status.ORDERED,
                TARGET_STATUSES, afterId, Limit.of(BATCH_SIZE));
            for (Long orderId : orderIds) {
                targets++;
                try {
                    orderService.cancelForUnavailableSeller(orderId, sellerId);
                } catch (RuntimeException e) {
                    failed++;
                    log.warn("판매 불가 판매자 주문 정리 실패: sellerId={} orderId={} exception={}", sellerId, orderId,
                        e.getClass().getSimpleName());
                }
            }
            if (orderIds.size() < BATCH_SIZE) {
                break;
            }
            afterId = orderIds.getLast();
        }
        log.info("판매 불가 판매자 주문 정리: sellerId={} targets={} failed={}", sellerId, targets, failed);
        if (failed > 0) {
            throw new IllegalStateException("판매 불가 판매자 주문 정리 일부 실패: sellerId=" + sellerId + " failed=" + failed);
        }
        return targets;
    }

    private boolean isUnavailable(Long sellerId) {
        return sellerReplicaRepository.findBySellerId(sellerId)
            .map(seller -> !SellerStatus.ACTIVE.name().equals(seller.getStatus()))
            .orElse(false);
    }
}
