package com.bbcc.kidly.boundedcontext.order.app.order;

import com.bbcc.kidly.boundedcontext.order.app.OrderActor;
import com.bbcc.kidly.boundedcontext.order.app.OrderErrorCode;
import com.bbcc.kidly.boundedcontext.order.app.OrderErrors;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.OrderDetail;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.PlaceOrderCommand;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.PlaceOrderResult;
import com.bbcc.kidly.boundedcontext.order.app.port.ChildReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderCartLines;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderStatusHistoryRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.ProductReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.domain.OrderRuleViolationException;
import com.bbcc.kidly.boundedcontext.order.domain.history.OrderStatusHistory;
import com.bbcc.kidly.boundedcontext.order.domain.order.Order;
import com.bbcc.kidly.boundedcontext.order.domain.order.OrderItem;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaChild;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaProduct;
import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.shared.order.event.OrderCreatedEvent;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주문하기(#1508, OrderService에서 분리): 주문 생성 · 요청 키 주문 · 같은 키 다시 읽기. 결제 반영 · 취소는 OrderService,
 * 구매확정은 OrderConfirmationService, 조회는 OrderQueryService다.
 * 로그: 상태가 바뀌면 INFO, 같은 요청을 다시 받으면 DEBUG. 개인 정보(배송지 · 연락처 · 자녀 이름 · 키 · 몸무게)는 남기지 않는다.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class OrderPlacementService {

    private final OrderRepository orderRepository;
    private final ProductReplicaRepository productReplicaRepository;
    private final ChildReplicaRepository childReplicaRepository;
    private final OrderStatusHistoryRepository historyRepository;
    private final Order.Policy orderPolicy;
    private final OrderQueryService orderQueries;
    private final OrderCartLines orderCartLines;
    private final OrderEventSupport events;

    /**
     * 복사본이 없는 옵션 · 자녀는 여기서 404. 나머지 검사(빈 항목 · 중복 · 수량 · 가격 · 판매 중 · 자기 상품 · 자녀)는
     * Order.place가 한다. 저장 뒤 order.created를 발행한다.
     */
    public OrderDetail place(Long memberId, PlaceOrderCommand command) {
        return create(memberId, command, null, null);
    }

    /**
     * 요청 키(Idempotency-Key 헤더, #351)가 있으면 같은 회원 · 같은 키의 주문을 먼저 찾는다. 같은 요청이면 그 주문의 지금 상세를, 다른 요청이면 409.
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
                return new PlaceOrderResult(orderQueries.detailOf(order.getId(), order), false);
            });
    }

    private OrderDetail create(Long memberId, PlaceOrderCommand command, String idempotencyKey, String requestHash) {
        LocalDateTime now = LocalDateTime.now();
        // 장바구니 칸으로 사면(#998) 그 칸이 구매자 칸이거나 구매자가 허용된 예약 칸인지 장바구니가 확인한다(아니면 403 · 404)
        List<OrderCartLines.Line> cartLines = command.items().stream()
            .filter(item -> item.cartItemId() != null)
            .map(item -> new OrderCartLines.Line(item.cartItemId(), item.optionId(), item.childId(),
                item.quantity()))
            .toList();
        if (!cartLines.isEmpty()) {
            orderCartLines.requireOrderable(memberId, cartLines);
        }
        List<Order.RequestedItem> requested = command.items().stream().map(this::toRequestedItem).toList();
        PlaceOrderCommand.ShippingAddress address = command.shippingAddress();

        Order placed;
        try {
            placed = Order.place(memberId, requested, new Order.ShippingAddress(
                address.recipientName(), address.phone(), address.zipCode(), address.address()), orderPolicy, now);
        } catch (OrderRuleViolationException e) {
            throw OrderErrors.toBusiness(e);
        }
        if (idempotencyKey != null) {
            placed.recordRequestKey(idempotencyKey, requestHash);
        }
        Order order = orderRepository.save(placed);
        historyRepository.save(OrderActor.buyer(memberId, OrderActor.PLACE)
            .createdHistory(order.getId(), OrderStatusHistory.TargetType.ORDER, order.getId(), order.getStatus(), now));

        events.publish(new OrderCreatedEvent(UUID.randomUUID(), now, new OrderCreatedEvent.Data(
            order.getId(),
            order.getMemberId(),
            order.getItems().stream()
                .map(item -> new OrderCreatedEvent.Item(
                    item.getId(), item.getProduct().optionId(), item.getQuantity()))
                .toList())));
        log.info("주문 생성: orderId={} itemCount={} totalAmount={}",
            order.getId(), order.getItems().size(), order.getTotalAmount());
        return OrderDetail.from(order, List.of(), orderQueries.sellerNamesOf(List.of(order)));
    }

    private Order.RequestedItem toRequestedItem(PlaceOrderCommand.Item item) {
        ReplicaProduct product = productReplicaRepository.findByOptionId(item.optionId())
            .orElseThrow(() -> new BusinessException(OrderErrorCode.PRODUCT_OPTION_NOT_FOUND));
        Order.RequestedChild child = item.childId() == null ? null : toRequestedChild(item.childId());
        return new Order.RequestedItem(
            new OrderItem.ProductSnapshot(product.getProductId(), product.getOptionId(), product.getSellerId(),
                product.getProductName(), product.getOptionName(), product.getSize(), product.unitPrice(),
                product.getCategoryId(), product.getThumbnailUrl()),
            product.isPurchasable(),
            child,
            item.quantity(),
            item.unitPrice(),
            item.cartItemId());
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
