package com.bbcc.kidly.boundedcontext.order.app.order;

import com.bbcc.kidly.boundedcontext.order.app.order.dto.PlaceOrderCommand;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.in.OrderPlacing;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 다른 컨텍스트(조르기)에 주문하기 · 결제 진행 조회를 내준다(#701, {@link OrderPlacing}). 다른 들어오는 포트 구현(app.provided)과
 * 달리 여기 둔다: 주문하기({@link OrderService})가 이 기능 패키지 안에 있기 때문이다. 요청 키(Idempotency-Key)는 쓰지 않는다
 * (부르는 쪽이 한 번만 부르게 막는다).
 */
@Service
@RequiredArgsConstructor
public class ProvidedPlacingService implements OrderPlacing {

    private final OrderService orderService;
    private final OrderRepository orderRepository;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Long place(Long memberId, List<Item> items, ShippingAddress shippingAddress) {
        PlaceOrderCommand command = new PlaceOrderCommand(
            items.stream()
                .map(item -> new PlaceOrderCommand.Item(item.optionId(), item.childId(), item.quantity(),
                    item.unitPrice()))
                .toList(),
            new PlaceOrderCommand.ShippingAddress(shippingAddress.recipientName(), shippingAddress.phone(),
                shippingAddress.zipCode(), shippingAddress.address()));
        return orderService.place(memberId, command).orderId();
    }

    @Override
    @Transactional
    public List<PaymentState> findPaymentStates(Collection<Long> orderIds) {
        if (orderIds.isEmpty()) {
            return List.of();
        }
        return orderRepository.findAllWithItemsByIdIn(orderIds).stream()
            .map(order -> new PaymentState(order.getId(), order.getStatus().name(), order.getPaidAt() != null))
            .toList();
    }
}
