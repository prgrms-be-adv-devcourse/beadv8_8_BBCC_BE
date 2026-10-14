package com.bbcc.kidly.boundedcontext.order.in.web;

import com.bbcc.kidly.boundedcontext.order.app.order.OrderService;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.PlaceOrderCommand;
import com.bbcc.kidly.boundedcontext.order.in.web.dto.OrderDetailResponse;
import com.bbcc.kidly.boundedcontext.order.in.web.dto.OrderSummaryResponse;
import com.bbcc.kidly.boundedcontext.order.in.web.dto.PlaceOrderRequest;
import com.bbcc.kidly.global.response.PageResponse;
import com.bbcc.kidly.global.security.AuthMember;
import com.bbcc.kidly.global.security.LoginMember;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 주문 API. 로그인한 보호자 본인의 주문만 다룬다.
 */
@RestController
@RequestMapping(OrderController.ORDERS_PATH)
@RequiredArgsConstructor
public class OrderController {

    public static final String ORDERS_PATH = "/api/v1/orders";

    private final OrderService orderService;

    /**
     * 201 + Location + 주문 상세. 결과(결제완료 · 취소)는 주문 상세로 확인한다.
     */
    @PostMapping
    public ResponseEntity<OrderDetailResponse> place(
        @LoginMember AuthMember member,
        @Valid @RequestBody PlaceOrderRequest request) {
        OrderDetailResponse body = OrderDetailResponse.from(orderService.place(member.id(), toCommand(request)));
        return ResponseEntity.created(URI.create(ORDERS_PATH + "/" + body.orderId())).body(body);
    }

    /**
     * 최근 주문부터. size는 1~100.
     */
    @GetMapping
    public PageResponse<OrderSummaryResponse> getOrders(
        @LoginMember AuthMember member,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.from(orderService.getOrders(member.id(), page, size).map(OrderSummaryResponse::from));
    }

    /**
     * 구매자가 주문상품을 직접 구매확정한다. 이미 확정이어도 200. 배송 전이면 409.
     */
    @PostMapping("/{orderId}/items/{orderItemId}/confirm")
    public OrderDetailResponse.Item confirm(
        @LoginMember AuthMember member,
        @PathVariable Long orderId,
        @PathVariable Long orderItemId) {
        return OrderDetailResponse.Item.from(orderService.confirmByBuyer(member.id(), orderId, orderItemId));
    }

    @GetMapping("/{orderId}")
    public OrderDetailResponse getOrder(@LoginMember AuthMember member, @PathVariable Long orderId) {
        return OrderDetailResponse.from(orderService.getOrder(member.id(), orderId));
    }

    private PlaceOrderCommand toCommand(PlaceOrderRequest request) {
        PlaceOrderRequest.ShippingAddress address = request.shippingAddress();
        return new PlaceOrderCommand(
            request.items().stream()
                .map(item -> new PlaceOrderCommand.Item(
                    item.optionId(), item.childId(), item.quantity(), item.unitPrice()))
                .toList(),
            new PlaceOrderCommand.ShippingAddress(
                address.recipientName(), address.phone(), address.zipCode(), address.address()));
    }
}
