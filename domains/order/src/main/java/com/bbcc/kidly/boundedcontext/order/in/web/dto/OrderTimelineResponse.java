package com.bbcc.kidly.boundedcontext.order.in.web.dto;

import com.bbcc.kidly.boundedcontext.order.app.order.dto.OrderTimeline;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 주문 처리 타임라인(#352). ID는 문자열, 단계 · 상태는 enum 이름.
 *
 * @param currentStep 가장 뒤의 DONE 또는 PARTIAL 단계
 * @param cancelReason 취소가 아니면 null
 * @param paymentDueAt 결제대기일 때만 값
 */
public record OrderTimelineResponse(
    String orderId,
    String status,
    String currentStep,
    String cancelReason,
    LocalDateTime paymentDueAt,
    List<Step> steps) {

    /**
     * @param details SHIPPED · DELIVERED는 배송 건별(DeliveryDetail), CONFIRMED는 주문상품별(ItemDetail), 그 밖 []
     */
    public record Step(String step, String status, LocalDateTime at, boolean approximate, List<Object> details) {
    }

    public record DeliveryDetail(String sellerOrderId, String sellerId, String sellerName, String status,
        LocalDateTime at) {
    }

    public record ItemDetail(String orderItemId, String productName, String status, LocalDateTime at,
        String confirmType) {
    }

    public static OrderTimelineResponse from(OrderTimeline timeline) {
        return new OrderTimelineResponse(
            toText(timeline.orderId()),
            timeline.status(),
            timeline.currentStep().name(),
            timeline.cancelReason(),
            timeline.paymentDueAt(),
            timeline.steps().stream().map(OrderTimelineResponse::toStep).toList());
    }

    // 단계마다 details는 배송 건 · 주문상품 중 한 종류뿐이라 둘을 이어 붙여도 한 종류만 남는다
    private static Step toStep(OrderTimeline.Step step) {
        List<Object> details = new ArrayList<>();
        step.deliveries().forEach(delivery -> details.add(new DeliveryDetail(toText(delivery.sellerOrderId()),
            toText(delivery.sellerId()), delivery.sellerName(), delivery.status().name(), delivery.at())));
        step.items().forEach(item -> details.add(new ItemDetail(toText(item.orderItemId()), item.productName(),
            item.status().name(), item.at(), item.confirmType())));
        return new Step(step.step().name(), step.status().name(), step.at(), step.approximate(), details);
    }

    private static String toText(Long id) {
        return id == null ? null : String.valueOf(id);
    }
}
