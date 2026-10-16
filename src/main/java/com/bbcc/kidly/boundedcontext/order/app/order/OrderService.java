package com.bbcc.kidly.boundedcontext.order.app.order;

import com.bbcc.kidly.boundedcontext.order.app.order.dto.AdminOrderSummary;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.AutoConfirmTarget;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.MarkPaidCommand;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.OrderDetail;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.OrderSummary;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.OrderTimeline;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.PlaceOrderCommand;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.PlaceOrderResult;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.StockDeductionFailedCommand;
import com.bbcc.kidly.boundedcontext.order.app.port.ChildReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.DeliveryRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.ProductReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.SellerReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.domain.OrderErrorCode;
import com.bbcc.kidly.boundedcontext.order.domain.delivery.Delivery;
import com.bbcc.kidly.boundedcontext.order.domain.order.Order;
import com.bbcc.kidly.boundedcontext.order.domain.order.OrderItem;
import com.bbcc.kidly.boundedcontext.order.domain.order.TransitionResult;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaChild;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaProduct;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaSeller;
import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.global.eventpublisher.EventPublisher;
import com.bbcc.kidly.shared.order.event.OrderCancelledEvent;
import com.bbcc.kidly.shared.order.event.OrderCreatedEvent;
import com.bbcc.kidly.shared.order.event.OrderItemPurchaseConfirmedEvent;
import com.bbcc.kidly.shared.order.event.OrderPaymentRequestedEvent;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주문하기 · 결제 요청 · 주문 조회. 남의 주문은 없는 주문으로 본다.
 * 로그: 상태가 바뀌면 INFO, 받은 요청을 무시하면 DEBUG(결과 · 현재 상태), 받은 이벤트의 주문이 없으면 WARN.
 * 개인 정보(배송지 · 연락처 · 자녀 이름 · 키 · 몸무게)는 남기지 않는다.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductReplicaRepository productReplicaRepository;
    private final ChildReplicaRepository childReplicaRepository;
    private final DeliveryRepository deliveryRepository;
    private final SellerReplicaRepository sellerReplicaRepository;
    private final EventPublisher eventPublisher;
    private final Order.Policy orderPolicy;

    /**
     * 복사본이 없는 옵션 · 자녀는 여기서 404. 나머지 검사(빈 항목 · 중복 · 수량 · 가격 · 판매 중 · 자기 상품 · 자녀)는
     * Order.place가 한다. 저장 뒤 order.created를 발행한다.
     */
    public OrderDetail place(Long memberId, PlaceOrderCommand command) {
        return create(memberId, command, null, null);
    }

    /**
     * 요청 키(#351)가 있으면 같은 회원 · 같은 키의 주문을 먼저 찾는다. 같은 요청이면 그 주문의 지금 상세를, 다른 요청이면 409.
     * 없으면 키 · 비교값을 기록해 만든다. 동시에 같은 키로 만들면 한쪽은 유니크 제약에 걸리고 OrderPlacement가 다시 읽는다.
     */
    public PlaceOrderResult placeIdempotently(Long memberId, PlaceOrderCommand command, String idempotencyKey,
        String requestHash) {
        if (idempotencyKey != null) {
            Optional<PlaceOrderResult> existing = replay(memberId, idempotencyKey, requestHash);
            if (existing.isPresent()) {
                return existing.get();
            }
        }
        return new PlaceOrderResult(create(memberId, command, idempotencyKey, requestHash), true);
    }

    /**
     * 같은 회원 · 같은 키로 이미 만든 주문을 읽는다. 비교값이 다르면 IDEMPOTENCY_KEY_REUSED.
     */
    @Transactional(readOnly = true)
    public Optional<PlaceOrderResult> replay(Long memberId, String idempotencyKey, String requestHash) {
        return orderRepository.findByMemberIdAndIdempotencyKey(memberId, idempotencyKey)
            .map(order -> {
                if (!order.getRequestHash().equals(requestHash)) {
                    throw new BusinessException(OrderErrorCode.IDEMPOTENCY_KEY_REUSED);
                }
                log.debug("주문 요청 반복: orderId={} result=same-request", order.getId());
                return new PlaceOrderResult(detailOf(order.getId(), order), false);
            });
    }

    private OrderDetail create(Long memberId, PlaceOrderCommand command, String idempotencyKey, String requestHash) {
        LocalDateTime now = LocalDateTime.now();
        List<Order.RequestedItem> requested = command.items().stream().map(this::toRequestedItem).toList();
        PlaceOrderCommand.ShippingAddress address = command.shippingAddress();

        Order placed = Order.place(memberId, requested,
            new Order.ShippingAddress(address.recipientName(), address.phone(), address.zipCode(), address.address()),
            orderPolicy, now);
        if (idempotencyKey != null) {
            placed.recordRequestKey(idempotencyKey, requestHash);
        }
        Order order = orderRepository.save(placed);

        eventPublisher.publish(new OrderCreatedEvent(UUID.randomUUID(), now, new OrderCreatedEvent.Data(
            order.getId(),
            order.getMemberId(),
            order.getItems().stream()
                .map(item -> new OrderCreatedEvent.Item(
                    item.getId(), item.getProduct().optionId(), item.getQuantity()))
                .toList())));
        log.info("주문 생성: orderId={} itemCount={} totalAmount={}",
            order.getId(), order.getItems().size(), order.getTotalAmount());
        return OrderDetail.from(order, List.of(), sellerNamesOf(List.of(order)));
    }

    /**
     * 재고 차감이 끝난 주문의 결제를 요청한다. 주문상품별 금액을 함께 보낸다(결제가 주문상품 단위로 결제 · 환불).
     * 주문이 없거나 이미 요청했거나 결제대기가 아니면 아무것도 하지 않는다.
     */
    public void requestPayment(Long orderId) {
        LocalDateTime now = LocalDateTime.now();
        Order order = findForEvent(orderId, "stock.deducted");
        if (order == null) {
            return;
        }
        TransitionResult result = order.requestPayment(now);
        if (result != TransitionResult.APPLIED) {
            logIgnored("stock.deducted", order, result);
            return;
        }
        eventPublisher.publish(new OrderPaymentRequestedEvent(UUID.randomUUID(), now,
            new OrderPaymentRequestedEvent.Data(order.getId(), order.getMemberId(), order.getTotalAmount(),
                order.getItems().stream()
                    .map(item -> new OrderPaymentRequestedEvent.Item(item.getId(), item.getAmount()))
                    .toList())));
        log.info("결제 요청: orderId={} amount={}", order.getId(), order.getTotalAmount());
    }

    /**
     * 결제완료로 바꾼다. 결제 시각은 이벤트 값이 아니라 지금 시각을 쓴다.
     * 결제대기면 주문이 회원 · 주문상품 구성 · 금액을 대조하고, 다르면 PaymentMismatchException이 그대로 나간다.
     * 취소된 주문에 온 결제 완료는 반영하지 않는다. 환불은 결제 쪽이 처리하므로 환불 여부 확인만 WARN으로 알린다
     * (#290, 올 때마다). 단 결제완료 뒤 구매자가 취소한 주문에 같은 결제 ID로 다시 온 결제 완료는 이미 반영한 중복이라
     * DEBUG로만 남긴다(#354-1, 그 결제의 환불은 취소 때 order.cancelled로 이미 요청됨).
     *
     * @return 이번에 결제완료로 바꿨으면 true. 주문이 없거나 결제대기가 아니면 false
     */
    public boolean markPaid(MarkPaidCommand command) {
        LocalDateTime now = LocalDateTime.now();
        Order order = findForEvent(command.orderId(), "payment.completed");
        if (order == null) {
            return false;
        }
        TransitionResult result = order.markPaid(command.paymentId(), command.memberId(), command.amount(),
            toPaidItems(command.items()), now);
        if (result == TransitionResult.NOT_APPLICABLE && order.getStatus() == Order.Status.CANCELLED) {
            // 결제 ID가 없으면 같은 결제인지 알 수 없으므로 중복으로 보지 않는다(환불 확인 WARN 그대로)
            if (order.getPaidAt() != null && command.paymentId() != null
                && Objects.equals(order.getPaymentId(), command.paymentId())) {
                logIgnored("payment.completed", order, result);
                return false;
            }
            log.warn("취소된 주문에 결제 완료 — 결제 쪽이 환불 처리, 환불 여부 확인 필요: event=payment.completed "
                    + "reason=paid-after-cancel "
                    + "orderId={} paymentId={} amount={} cancelReason={}",
                command.orderId(), command.paymentId(), command.amount(), order.getCancelReason());
            return false;
        }
        if (result != TransitionResult.APPLIED) {
            logIgnored("payment.completed", order, result);
            return false;
        }
        log.info("결제완료: orderId={} paymentId={}", order.getId(), command.paymentId());
        return true;
    }

    /**
     * 비어 있으면(null) 빈 목록으로 넘겨 주문이 구성 불일치로 판단하게 한다. 빈 칸(null)도 그대로 넘긴다.
     */
    private static List<Order.PaidItem> toPaidItems(List<MarkPaidCommand.Item> items) {
        if (items == null) {
            return List.of();
        }
        return items.stream()
            .map(item -> item == null ? null : new Order.PaidItem(item.orderItemId(), item.amount()))
            .toList();
    }

    /**
     * 결제 실패로 취소한다. 결제대기가 아니면 그대로 둔다.
     */
    public void cancelForPaymentFailure(Long orderId) {
        cancel(orderId, "payment.failed", order -> order.cancel(Order.CancelReason.PAYMENT_FAILED));
    }

    /**
     * 재고 차감 실패로 취소하고 주문상품별 사유를 남긴다. 결제대기가 아니면 그대로 둔다(사유도 남기지 않음).
     * 주문에 없는 주문상품 ID는 주문이 무시하므로 여기서 WARN만 남긴다.
     */
    public void cancelForStockDeductionFailure(StockDeductionFailedCommand command) {
        Map<Long, String> reasons = command.items().stream()
            .collect(Collectors.toMap(StockDeductionFailedCommand.Item::orderItemId,
                StockDeductionFailedCommand.Item::reason, (first, last) -> last));
        cancel(command.orderId(), "stock.deduction-failed", order -> {
            warnUnknownOrderItems(command.orderId(), order, reasons);
            return order.cancelForOutOfStock(reasons);
        });
    }

    private static void warnUnknownOrderItems(Long orderId, Order order, Map<Long, String> reasons) {
        List<Long> orderItemIds = order.getItems().stream().map(OrderItem::getId).toList();
        reasons.keySet().stream()
            .filter(orderItemId -> !orderItemIds.contains(orderItemId))
            .forEach(orderItemId -> log.warn("재고 차감 실패 주문상품 없음: event=stock.deduction-failed orderId={} "
                + "orderItemId={} reason=unknown-order-item", orderId, orderItemId));
    }

    /**
     * 결제 마감 만료로 취소한다. 마감이 지났는지는 주문이 직접 확인한다(마감 전이면 그대로).
     * 결제대기가 아니면 그대로 둔다(그 사이 결제완료됐으면 결제완료가 이긴다).
     */
    public void cancelForPaymentExpiry(Long orderId) {
        LocalDateTime now = LocalDateTime.now();
        cancel(orderId, "payment-expiry", order -> order.expirePayment(now));
    }

    /**
     * 결제대기이고 결제 마감이 now(포함) 이전인 주문 ID. afterId 다음부터 주문 ID 오름차순 limit건.
     * now는 스케줄러가 한 실행에 한 번 정해 모든 배치에 같은 값을 넘긴다.
     */
    @Transactional(readOnly = true)
    public List<Long> findOverduePaymentOrderIds(LocalDateTime now, Long afterId, int limit) {
        return orderRepository.findOverduePaymentOrderIds(Order.Status.PENDING_PAYMENT, now, afterId, Limit.of(limit));
    }

    /**
     * 구매자가 직접 구매확정한다. 없거나 남의 주문이면 404. 이미 확정이어도 주문상품을 돌려준다.
     */
    public OrderDetail.Item confirmByBuyer(Long memberId, Long orderId, Long orderItemId) {
        LocalDateTime now = LocalDateTime.now();
        Order order = orderRepository.findByIdAndMemberId(orderId, memberId)
            .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
        confirm(order, orderItemId, "confirm-by-buyer", target -> target.confirmItem(orderItemId, now));
        OrderItem item = findItem(order, orderItemId);
        String sellerName = sellerReplicaRepository.findBySellerId(item.getProduct().sellerId())
            .map(ReplicaSeller::getBusinessName)
            .orElse(null);
        return OrderDetail.Item.from(item, sellerName);
    }

    /**
     * 자동 구매확정. 기간이 지났는지는 주문이 직접 확인한다(기간 전 · 이미 확정이면 그대로). 주문이 없으면 무시.
     */
    public void confirmAutomatically(Long orderId, Long orderItemId) {
        LocalDateTime now = LocalDateTime.now();
        Order order = findForEvent(orderId, "auto-confirm");
        if (order == null) {
            return;
        }
        confirm(order, orderItemId, "auto-confirm", target -> target.autoConfirmItem(orderItemId, orderPolicy, now));
    }

    /**
     * 자동 구매확정 대상을 좁힌다: 주문됨이고 (now − 자동 구매확정 기간) 이전(포함)에 배송 완료된 주문상품.
     * afterId 다음부터 주문상품 ID 오름차순 limit건. now는 스케줄러가 한 실행에 한 번 정해 넘긴다.
     */
    @Transactional(readOnly = true)
    public List<AutoConfirmTarget> findAutoConfirmTargets(LocalDateTime now, Long afterId, int limit) {
        LocalDateTime deliveredBefore = now.minus(orderPolicy.autoConfirmAfter());
        return orderRepository.findAutoConfirmTargets(
                OrderItem.Status.ORDERED, deliveredBefore, afterId, Limit.of(limit))
            .stream()
            .map(row -> new AutoConfirmTarget(row.getOrderId(), row.getOrderItemId()))
            .toList();
    }

    /**
     * 구매자가 주문을 전체 취소한다(#354). 결제대기 주문, 또는 판매자별 배송이 모두 발송 전인 결제완료 주문만.
     * 주문 취소 · 판매자 배송 취소 · order.cancelled 발행을 한 트랜잭션으로 한다(결제 환불 · 재고 복구는 이벤트를 받은 쪽).
     * 판매자 발송과 동시에 오면 배송 @Version으로 한쪽만 성공한다(진 쪽은 공통 409, 다시 누르면 아래 상태 검사로 409).
     * 이미 취소된 주문은(사유 무관) 그대로 상세를 돌려준다. 남의 주문은 없는 주문으로 본다.
     * 배송 완료 · 구매확정은 배송이 발송된 뒤에만 일어나므로 발송 검사가 그 경합도 막는다.
     */
    public OrderDetail cancelByBuyer(Long memberId, Long orderId) {
        LocalDateTime now = LocalDateTime.now();
        Order order = orderRepository.findByIdAndMemberId(orderId, memberId)
            .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
        List<Delivery> deliveries = deliveryRepository.findAllByOrderIdOrderByIdAsc(orderId);
        if (order.getStatus() != Order.Status.CANCELLED && deliveries.stream().anyMatch(OrderService::isShipped)) {
            throw new BusinessException(OrderErrorCode.CANCEL_NOT_ALLOWED_SHIPPED);
        }
        TransitionResult result = order.cancel(Order.CancelReason.BUYER_CANCELLED);
        if (result == TransitionResult.NOT_APPLICABLE) {
            throw new BusinessException(OrderErrorCode.CANCEL_NOT_ALLOWED_SHIPPED);
        }
        if (result == TransitionResult.APPLIED) {
            deliveries.forEach(Delivery::cancel);
            publishCancelled(order, now);
        } else {
            logIgnored("cancel-by-buyer", order, result);
        }
        return OrderDetail.from(order, deliveries, sellerNamesOf(List.of(order)));
    }

    private static boolean isShipped(Delivery delivery) {
        return delivery.getStatus() == Delivery.Status.SHIPPING || delivery.getStatus() == Delivery.Status.DELIVERED;
    }

    @Transactional(readOnly = true)
    public OrderDetail getOrder(Long memberId, Long orderId) {
        return orderRepository.findByIdAndMemberId(orderId, memberId)
            .map(order -> detailOf(orderId, order))
            .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
    }

    private OrderDetail detailOf(Long orderId, Order order) {
        return OrderDetail.from(order, deliveryRepository.findAllByOrderIdOrderByIdAsc(orderId),
            sellerNamesOf(List.of(order)));
    }

    /**
     * 주문(주문상품 포함) · 배송 건 · 판매자 상호명을 한 번씩 읽어 타임라인을 만든다(#352). 남의 주문은 없는 주문으로 본다.
     */
    @Transactional(readOnly = true)
    public OrderTimeline getTimeline(Long memberId, Long orderId) {
        Order order = orderRepository.findWithItemsByIdAndMemberId(orderId, memberId)
            .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
        return OrderTimeline.from(order, deliveryRepository.findAllByOrderIdOrderByIdAsc(orderId),
            sellerNamesOf(List.of(order)));
    }

    /**
     * 최근 주문부터. 그 페이지 주문들의 주문상품 · 판매자 상호명은 한 번씩 읽어 합친다(주문 수와 상관없이 쿼리 수 일정).
     * 주문상품을 읽지 못한 주문은 목록 전체를 실패시키지 않고 대표 상품명 없이 내리고 WARN만 남긴다.
     */
    @Transactional(readOnly = true)
    public Page<OrderSummary> getOrders(Long memberId, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size);
        // 저장소는 offset(page × size)이 int를 넘으면 조회하지 못하므로(500), 조회 없이 빈 페이지로 답한다
        if (pageable.getOffset() > Integer.MAX_VALUE) {
            return new PageImpl<>(List.of(), pageable, orderRepository.countByMemberId(memberId));
        }
        Page<Order> orders = orderRepository.findAllByMemberIdOrderByIdDesc(memberId, pageable);
        if (orders.isEmpty()) {
            return orders.map(OrderSummary::withoutItems);
        }
        Map<Long, Order> withItems = orderRepository.findAllWithItemsByIdIn(
                orders.getContent().stream().map(Order::getId).toList())
            .stream()
            .collect(Collectors.toMap(Order::getId, Function.identity()));
        Map<Long, String> sellerNames = sellerNamesOf(withItems.values());
        return orders.map(order -> {
            Order loaded = withItems.get(order.getId());
            if (loaded == null) {
                log.warn("주문 목록 주문상품 없음: reason=order-items-not-found orderId={}", order.getId());
                return OrderSummary.withoutItems(order);
            }
            return OrderSummary.from(loaded, sellerNames);
        });
    }

    /**
     * 관리자 전체 주문 목록. 최근 주문부터, 그 페이지 주문들의 주문상품은 한 번에 읽어 주문상품 수를 센다
     * (주문 수와 상관없이 쿼리 수 일정). 주문상품을 읽지 못한 주문은 주문상품 수 0으로 내리고 WARN만 남긴다.
     */
    @Transactional(readOnly = true)
    public Page<AdminOrderSummary> getAllOrders(int page, int size) {
        PageRequest pageable = PageRequest.of(page, size);
        // 저장소는 offset(page × size)이 int를 넘으면 조회하지 못하므로(500), 조회 없이 빈 페이지로 답한다
        if (pageable.getOffset() > Integer.MAX_VALUE) {
            return new PageImpl<>(List.of(), pageable, orderRepository.count());
        }
        Page<Order> orders = orderRepository.findAllByOrderByIdDesc(pageable);
        if (orders.isEmpty()) {
            return orders.map(AdminOrderSummary::withoutItems);
        }
        Map<Long, Order> withItems = orderRepository.findAllWithItemsByIdIn(
                orders.getContent().stream().map(Order::getId).toList())
            .stream()
            .collect(Collectors.toMap(Order::getId, Function.identity()));
        return orders.map(order -> {
            Order loaded = withItems.get(order.getId());
            if (loaded == null) {
                log.warn("관리자 주문 목록 주문상품 없음: reason=order-items-not-found orderId={}", order.getId());
                return AdminOrderSummary.withoutItems(order);
            }
            return AdminOrderSummary.from(loaded);
        });
    }

    /**
     * 주문들의 주문상품 판매자 상호명을 한 번에 읽는다(판매자마다 조회하지 않음). 복사본이 없는 판매자는 빠진다.
     */
    private Map<Long, String> sellerNamesOf(Collection<Order> orders) {
        Set<Long> sellerIds = orders.stream()
            .flatMap(order -> order.getItems().stream())
            .map(item -> item.getProduct().sellerId())
            .collect(Collectors.toSet());
        if (sellerIds.isEmpty()) {
            return Map.of();
        }
        return sellerReplicaRepository.findAllBySellerIdIn(sellerIds).stream()
            .collect(Collectors.toMap(ReplicaSeller::getSellerId, ReplicaSeller::getBusinessName));
    }

    /**
     * 주문이 없으면 무시. 이번에 취소했을 때만 order.cancelled를 발행한다(사유는 주문에 기록된 값,
     * 주문상품 순서 그대로).
     *
     * @param source 취소를 일으킨 이벤트 · 작업 이름(로그용)
     * @param change 주문을 취소하는 도메인 메서드. 이번에 취소했으면 APPLIED
     */
    private void cancel(Long orderId, String source, Function<Order, TransitionResult> change) {
        LocalDateTime now = LocalDateTime.now();
        Order order = findForEvent(orderId, source);
        if (order == null) {
            return;
        }
        TransitionResult result = change.apply(order);
        if (result != TransitionResult.APPLIED) {
            logIgnored(source, order, result);
            return;
        }
        publishCancelled(order, now);
    }

    /**
     * order.cancelled를 주문상품 순서대로 발행하고 결과 한 줄을 남긴다(사유는 주문에 기록된 값).
     */
    private void publishCancelled(Order order, LocalDateTime now) {
        eventPublisher.publish(new OrderCancelledEvent(UUID.randomUUID(), now,
            new OrderCancelledEvent.Data(order.getId(), order.getMemberId(), order.getCancelReason().name(),
                order.getItems().stream()
                    .map(item -> new OrderCancelledEvent.Item(
                        item.getId(), item.getProduct().optionId(), item.getQuantity()))
                    .toList())));
        log.info("주문 취소: orderId={} reason={}", order.getId(), order.getCancelReason());
    }

    /**
     * 받은 이벤트(또는 시간 작업)의 주문을 찾는다. 없으면 WARN을 남기고 null.
     */
    private Order findForEvent(Long orderId, String event) {
        return orderRepository.findById(orderId).orElseGet(() -> {
            log.warn("이벤트의 주문 없음: event={} orderId={}", event, orderId);
            return null;
        });
    }

    private void logIgnored(String event, Order order, TransitionResult result) {
        log.debug("이벤트 무시: event={} orderId={} result={} status={}",
            event, order.getId(), result, order.getStatus());
    }

    /**
     * 이번에 확정했을 때만 order-item.purchase-confirmed를 발행한다.
     *
     * @param source 확정을 일으킨 요청 이름(로그용)
     * @param change 주문상품을 확정하는 도메인 메서드. 이번에 확정했으면 APPLIED
     */
    private void confirm(Order order, Long orderItemId, String source, Function<Order, TransitionResult> change) {
        TransitionResult result = change.apply(order);
        if (result != TransitionResult.APPLIED) {
            log.debug("이벤트 무시: event={} orderId={} orderItemId={} result={} status={}",
                source, order.getId(), orderItemId, result, order.getStatus());
            return;
        }
        OrderItem item = findItem(order, orderItemId);
        OrderItem.ProductSnapshot product = item.getProduct();
        eventPublisher.publish(new OrderItemPurchaseConfirmedEvent(UUID.randomUUID(), item.getConfirmedAt(),
            new OrderItemPurchaseConfirmedEvent.Data(
                item.getId(),
                order.getId(),
                order.getMemberId(),
                product.sellerId(),
                item.getChild() == null ? null : item.getChild().childId(),
                product.productId(),
                product.optionId(),
                product.size(),
                item.getQuantity(),
                item.getAmount(),
                item.getConfirmedAt())));
        log.info("구매확정: orderId={} orderItemId={} confirmType={}",
            order.getId(), item.getId(), item.getConfirmType());
    }

    private OrderItem findItem(Order order, Long orderItemId) {
        return order.getItems().stream()
            .filter(item -> Objects.equals(item.getId(), orderItemId))
            .findFirst()
            .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_ITEM_NOT_FOUND));
    }

    private Order.RequestedItem toRequestedItem(PlaceOrderCommand.Item item) {
        ReplicaProduct product = productReplicaRepository.findByOptionId(item.optionId())
            .orElseThrow(() -> new BusinessException(OrderErrorCode.PRODUCT_OPTION_NOT_FOUND));
        Order.RequestedChild child = item.childId() == null ? null : toRequestedChild(item.childId());
        return new Order.RequestedItem(
            new OrderItem.ProductSnapshot(product.getProductId(), product.getOptionId(), product.getSellerId(),
                product.getProductName(), product.getOptionName(), product.getSize(), product.unitPrice(),
                product.getCategoryId()),
            product.isPurchasable(),
            child,
            item.quantity(),
            item.unitPrice());
    }

    private Order.RequestedChild toRequestedChild(Long childId) {
        ReplicaChild child = childReplicaRepository.findByChildId(childId)
            .orElseThrow(() -> new BusinessException(OrderErrorCode.CHILD_NOT_FOUND));
        return new Order.RequestedChild(
            new OrderItem.ChildSnapshot(child.getChildId(), child.getName(), child.getHeightCm(), child.getWeightKg(),
                child.getBodyType()),
            child.getMemberId(),
            child.isDeleted());
    }
}
