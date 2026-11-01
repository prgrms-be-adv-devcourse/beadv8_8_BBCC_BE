package com.bbcc.kidly.boundedcontext.delivery.app;

import com.bbcc.kidly.boundedcontext.delivery.app.dto.DeliveryResult;
import com.bbcc.kidly.boundedcontext.delivery.app.dto.SellerOrderView;
import com.bbcc.kidly.boundedcontext.delivery.app.dto.TrackingTarget;
import com.bbcc.kidly.boundedcontext.delivery.app.port.DeliveryOrders;
import com.bbcc.kidly.boundedcontext.delivery.app.port.DeliveryOrders.Order;
import com.bbcc.kidly.boundedcontext.delivery.app.port.DeliveryRepository;
import com.bbcc.kidly.boundedcontext.delivery.app.port.in.DeliveryOperations;
import com.bbcc.kidly.boundedcontext.delivery.app.port.in.ShippingDelays;
import com.bbcc.kidly.boundedcontext.delivery.domain.Delivery;
import com.bbcc.kidly.boundedcontext.delivery.domain.DeliveryRuleViolationException;
import com.bbcc.kidly.global.error.BusinessException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 판매자별 주문(배송): 결제완료 뒤 만들기 · 발송 · 배송 완료 · 판매자 목록. 남의 배송은 없는 배송으로 본다.
 * 주문 읽기 · 배송 완료 시각 반영 · 상태 이력은 {@link DeliveryOrders}로 한다(주문 컨텍스트, #641).
 * 주문이 쓰는 기능은 {@link DeliveryOperations} · {@link ShippingDelays}로 내준다.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class DeliveryService implements DeliveryOperations, ShippingDelays {

    // 상태 이력의 주체 이름(이벤트 이름은 주문 OrderActor.PAYMENT_COMPLETED와 같은 값)
    private static final String PAYMENT_COMPLETED = "payment.completed";
    private static final String SELLER_SHIP = "POST /api/v1/seller/orders/{sellerOrderId}/ship";
    private static final String SELLER_DELIVER = "POST /api/v1/seller/orders/{sellerOrderId}/deliver";
    private static final String SELLER = "SELLER";
    // 택배 조회 스케줄러가 바꾼 상태 이력의 주체(#650, 주문 OrderActor의 scheduler:{작업} 이름 규칙과 같음)
    private static final String SYSTEM = "SYSTEM";
    static final String DELIVERY_TRACKING = "scheduler:delivery-tracking";


    private final DeliveryRepository deliveryRepository;
    private final DeliveryOrders deliveryOrders;

    /**
     * 주문상품을 판매자로 묶어 판매자마다 배송 준비 1건을 만든다. 주문이 없으면 만들지 않는다.
     */
    @Override
    public void createFor(Long orderId) {
        createFor(orderId, new DeliveryActor("EVENT", null, PAYMENT_COMPLETED));
    }

    /**
     * 이벤트가 아닌 주체(관리자 재처리)가 만든다. 상태 이력에 그 주체를 남긴다(#442).
     */
    @Override
    public void createFor(Long orderId, DeliveryActor actor) {
        LocalDateTime now = LocalDateTime.now();
        DeliveryOrders.Actor historyActor = new DeliveryOrders.Actor(actor.type(), actor.id(), actor.source());
        deliveryOrders.findOrder(orderId).ifPresent(order -> order.items().stream()
            .map(Order.Item::sellerId)
            .distinct()
            .map(sellerId -> Delivery.create(orderId, sellerId))
            .forEach(delivery -> {
                deliveryRepository.save(delivery);
                deliveryOrders.recordHistory(orderId, delivery.getId(), historyActor, null,
                    delivery.getStatus().name(), null, now);
            }));
    }

    @Override
    public List<DeliveryView> findByOrder(Long orderId) {
        return deliveryRepository.findAllByOrderIdOrderByIdAsc(orderId).stream().map(DeliveryService::toView).toList();
    }

    @Override
    public Page<ShippingDelayedView> findShippingDelayed(LocalDateTime before, Pageable pageable) {
        return deliveryRepository.findShippingDelayed(before, pageable)
            .map(row -> new ShippingDelayedView(row.getOrderId(), row.getDeliveryId(), row.getCreatedAt()));
    }

    @Override
    public long countShippingDelayed(LocalDateTime before) {
        return deliveryRepository.countShippingDelayed(before);
    }

    @Override
    public List<DeliveryChange> cancelByOrder(Long orderId) {
        return deliveryRepository.findAllByOrderIdOrderByIdAsc(orderId).stream()
            .map(delivery -> {
                Delivery.Status from = delivery.getStatus();
                delivery.cancel();
                return new DeliveryChange(from.name(), toView(delivery));
            })
            .toList();
    }

    /**
     * 확인 순서: 모르는 택배사 400 → 없거나 남의 배송 404 → 발송 전이 아니면 409.
     */
    public DeliveryResult ship(Long memberId, Long deliveryId, String carrier, String trackingNumber) {
        Delivery.Carrier parsed = toCarrier(carrier);
        Delivery delivery = findOwned(memberId, deliveryId);
        LocalDateTime now = LocalDateTime.now();
        Delivery.Status from = delivery.getStatus();
        try {
            delivery.ship(parsed, trackingNumber, now);
        } catch (DeliveryRuleViolationException e) {
            throw DeliveryErrors.toBusiness(e);
        }
        deliveryOrders.recordHistory(delivery.getOrderId(), delivery.getId(), new DeliveryOrders.Actor(SELLER, memberId,
            SELLER_SHIP), from.name(), delivery.getStatus().name(), null, now);
        log.info("발송: orderId={} deliveryId={} sellerId={} carrier={}",
            delivery.getOrderId(), delivery.getId(), delivery.getSellerId(), parsed);
        return DeliveryResult.from(delivery);
    }

    /**
     * 배송 완료 → 그 판매자 몫 주문상품에 배송 완료 시각 기록을 한 트랜잭션으로 처리한다(뒤 단계가 실패하면 배송도 되돌림).
     * 이미 완료면 그대로 처음 값을 돌려주고, 주문상품 기록도 처음 시각을 유지한다(다시 불러도 같음). 발송 전이면 409.
     * 주문이 없으면 주문상품 기록은 건너뛴다.
     */
    public DeliveryResult complete(Long memberId, Long deliveryId) {
        Delivery delivery = findOwned(memberId, deliveryId);
        LocalDateTime now = LocalDateTime.now();
        Delivery.Status from = delivery.getStatus();
        boolean alreadyDelivered = from == Delivery.Status.DELIVERED;
        try {
            delivery.complete(now);
        } catch (DeliveryRuleViolationException e) {
            throw DeliveryErrors.toBusiness(e);
        }
        deliveryOrders.recordDelivered(delivery.getOrderId(), delivery.getSellerId(), delivery.getDeliveredAt());
        if (alreadyDelivered) {
            log.debug("요청 무시: event=deliver orderId={} deliveryId={} result=ALREADY_DONE",
                delivery.getOrderId(), delivery.getId());
        } else {
            deliveryOrders.recordHistory(delivery.getOrderId(), delivery.getId(), new DeliveryOrders.Actor(SELLER,
                memberId, SELLER_DELIVER), from.name(), delivery.getStatus().name(), null, now);
            log.info("배송 완료: orderId={} deliveryId={} sellerId={}",
                delivery.getOrderId(), delivery.getId(), delivery.getSellerId());
        }
        return DeliveryResult.from(delivery);
    }

    /**
     * 택배 조회 대상(#650): 발송 중이고 조회를 멈추지 않은 배송을 ID 커서로 limit건.
     */
    @Transactional(readOnly = true)
    public List<TrackingTarget> findTrackingTargets(Long afterId, int limit) {
        return deliveryRepository.findTrackingTargets(Delivery.Status.SHIPPING, afterId, Limit.of(limit)).stream()
            .map(TrackingTarget::from)
            .toList();
    }

    /**
     * 택배 조회로 배송 완료 + 그 판매자 몫 주문상품 deliveredAt 기록 + 상태 이력을 한 트랜잭션으로 처리한다(#650).
     * 완료해도 되는지는 Delivery가 판단한다(이미 완료 · 취소 · 조회 중지 · 택배사 시각이 발송보다 앞이면 그대로 둔다).
     * 판매자 수동 완료와 겹치면 @Version으로 한쪽만 성공하고, 진 쪽은 예외로 끝나 다음 주기에 대상에서 빠진다.
     *
     * @return 이번에 배송 완료로 바꿨으면 true
     */
    public boolean completeByTracking(Long deliveryId, LocalDateTime trackedAt) {
        Delivery delivery = deliveryRepository.findById(deliveryId)
            .orElseThrow(() -> new BusinessException(DeliveryErrorCode.DELIVERY_NOT_FOUND));
        Delivery.Status from = delivery.getStatus();
        if (!delivery.completeByTracking(trackedAt)) {
            log.warn("택배 완료 무시: orderId={} deliveryId={} status={} reason=not-applicable",
                delivery.getOrderId(), deliveryId, from);
            return false;
        }
        deliveryOrders.recordDelivered(delivery.getOrderId(), delivery.getSellerId(), delivery.getDeliveredAt());
        deliveryOrders.recordHistory(delivery.getOrderId(), delivery.getId(), new DeliveryOrders.Actor(SYSTEM, null,
            DELIVERY_TRACKING), from.name(), delivery.getStatus().name(), null, LocalDateTime.now());
        log.info("배송 완료: orderId={} deliveryId={} sellerId={} by=tracking",
            delivery.getOrderId(), delivery.getId(), delivery.getSellerId());
        return true;
    }

    /**
     * 택배 조회 중지(#650, 운송장 번호 형식 오류). 배송 상태는 바뀌지 않으므로 상태 이력은 남기지 않는다.
     *
     * @return 이번에 기록했으면 true
     */
    public boolean stopTracking(Long deliveryId, String reason) {
        Delivery delivery = deliveryRepository.findById(deliveryId)
            .orElseThrow(() -> new BusinessException(DeliveryErrorCode.DELIVERY_NOT_FOUND));
        boolean stopped = delivery.stopTracking(LocalDateTime.now());
        if (stopped) {
            log.warn("택배 조회 중지: orderId={} deliveryId={} reason={}", delivery.getOrderId(), deliveryId, reason);
        }
        return stopped;
    }

    /**
     * 판매자의 배송을 최근 것부터. 배송 페이지 1번 + 그 페이지 주문(주문상품 포함) 1번으로 읽는다(건마다 조회하지 않음).
     * 주문을 읽지 못한 배송은 목록 전체를 실패시키지 않고 배송 값만 내리고(주문 값은 비움) WARN만 남긴다.
     */
    @Transactional(readOnly = true)
    public Page<SellerOrderView> getSellerOrders(Long memberId, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size);
        // 저장소는 offset(page × size)이 int를 넘으면 조회하지 못하므로(500), 조회 없이 빈 페이지로 답한다
        if (pageable.getOffset() > Integer.MAX_VALUE) {
            return new PageImpl<>(List.of(), pageable, deliveryRepository.countBySellerId(memberId));
        }
        Page<Delivery> deliveries = deliveryRepository.findAllBySellerIdOrderByIdDesc(memberId, pageable);
        Map<Long, Order> ordersById = findOrdersWithItems(deliveries.getContent());
        return deliveries.map(delivery -> {
            Order order = ordersById.get(delivery.getOrderId());
            if (order == null) {
                log.warn("판매 주문 목록 주문 없음: reason=order-not-found deliveryId={} orderId={}",
                    delivery.getId(), delivery.getOrderId());
                return SellerOrderView.withoutOrder(delivery);
            }
            return SellerOrderView.of(delivery, order);
        });
    }

    private Map<Long, Order> findOrdersWithItems(List<Delivery> deliveries) {
        if (deliveries.isEmpty()) {
            return Map.of();
        }
        List<Long> orderIds = deliveries.stream().map(Delivery::getOrderId).distinct().toList();
        return deliveryOrders.findOrdersWithItems(orderIds).stream()
            .collect(Collectors.toMap(Order::orderId, Function.identity()));
    }

    private Delivery findOwned(Long memberId, Long deliveryId) {
        return deliveryRepository.findByIdAndSellerId(deliveryId, memberId)
            .orElseThrow(() -> new BusinessException(DeliveryErrorCode.DELIVERY_NOT_FOUND));
    }

    private Delivery.Carrier toCarrier(String carrier) {
        return Arrays.stream(Delivery.Carrier.values())
            .filter(candidate -> candidate.name().equals(carrier))
            .findFirst()
            .orElseThrow(() -> new BusinessException(DeliveryErrorCode.INVALID_CARRIER));
    }

    private static DeliveryView toView(Delivery delivery) {
        return new DeliveryView(delivery.getId(), delivery.getOrderId(), delivery.getSellerId(),
            delivery.getStatus().name(), delivery.getCarrier() == null ? null : delivery.getCarrier().name(),
            delivery.getTrackingNumber(), delivery.getShippedAt(), delivery.getDeliveredAt(),
            delivery.getStatus() == Delivery.Status.SHIPPING || delivery.getStatus() == Delivery.Status.DELIVERED);
    }
}
