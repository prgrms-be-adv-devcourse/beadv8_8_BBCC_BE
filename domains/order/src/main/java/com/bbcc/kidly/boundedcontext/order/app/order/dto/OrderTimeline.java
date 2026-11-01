package com.bbcc.kidly.boundedcontext.order.app.order.dto;

import com.bbcc.kidly.boundedcontext.order.app.port.OrderDeliveries.Delivery;
import com.bbcc.kidly.boundedcontext.order.domain.order.Order;
import com.bbcc.kidly.boundedcontext.order.domain.order.OrderItem;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * 주문 처리 타임라인(#352). 이미 저장된 시각만으로 단계를 조립한다(새 이력 없음).
 * 단계는 순서 있는 배열이다: CREATED → PAYMENT_REQUESTED → PAID → SHIPPED → DELIVERED → CONFIRMED.
 * 취소된 주문은 마지막으로 끝난 단계 뒤에 CANCELLED를 두고(결제 전 취소면 PAID 없음) 나머지를 NOT_APPLICABLE로 둔다.
 * 결제 뒤 구매자 취소(#354)면 나머지 단계의 details에 배송 건 · 주문상품을 NOT_APPLICABLE로 나열한다(결제 전 취소는 []).
 *
 * @param currentStep 배열에서 가장 뒤의 DONE 또는 PARTIAL 단계
 * @param cancelReason 취소가 아니면 null
 * @param paymentDueAt 결제대기일 때만 값
 */
public record OrderTimeline(
    Long orderId,
    String status,
    StepName currentStep,
    String cancelReason,
    LocalDateTime paymentDueAt,
    List<Step> steps) {

    public enum StepName {
        CREATED,
        PAYMENT_REQUESTED,
        PAID,
        CANCELLED,
        SHIPPED,
        DELIVERED,
        CONFIRMED
    }

    public enum StepStatus {
        DONE,
        PARTIAL,
        WAITING,
        NOT_APPLICABLE
    }

    /**
     * 단계 하나. 단계마다 details는 한 종류뿐이다: SHIPPED · DELIVERED는 deliveries, CONFIRMED는 items, 그 밖은 둘 다 [].
     * (app에는 인터페이스를 두지 않으므로 두 종류를 목록 둘로 나눠 담는다)
     *
     * @param at DONE이면 완료 시각(여러 건이면 가장 늦은 건), 그 밖 null
     * @param approximate at을 다른 시각으로 갈음했으면 true(CANCELLED는 updatedAt)
     */
    public record Step(StepName step, StepStatus status, LocalDateTime at, boolean approximate,
        List<DeliveryDetail> deliveries, List<ItemDetail> items) {
    }

    /**
     * @param sellerName 판매자의 지금 상호명. 판매자 복사본이 없으면 null
     */
    public record DeliveryDetail(Long sellerOrderId, Long sellerId, String sellerName, StepStatus status,
        LocalDateTime at) {
    }

    /**
     * @param productName 주문 당시 상품명. 상품 스냅샷이 없는 옛 주문상품이면 null
     * @param confirmType 확정 전이면 null
     */
    public record ItemDetail(Long orderItemId, String productName, StepStatus status, LocalDateTime at,
        String confirmType) {
    }

    /**
     * @param deliveries 주문의 배송 건(ID 오름차순). 결제 전이거나 배송 건 생성 이벤트를 놓쳤으면 비어 있거나 일부만 있다
     * @param sellerNames 판매자 ID별 지금 상호명. 없는 판매자는 null로 둔다
     */
    public static OrderTimeline from(Order order, List<Delivery> deliveries, Map<Long, String> sellerNames) {
        boolean cancelled = order.getStatus() == Order.Status.CANCELLED;
        List<Step> steps = new ArrayList<>();
        steps.add(done(StepName.CREATED, order.getCreatedAt()));
        steps.add(paymentRequested(order, cancelled));
        if (order.getPaidAt() != null) {
            steps.add(done(StepName.PAID, order.getPaidAt()));
        } else if (!cancelled) {
            steps.add(simple(StepName.PAID, StepStatus.WAITING));
        }
        if (cancelled) {
            steps.add(new Step(StepName.CANCELLED, StepStatus.DONE, order.getUpdatedAt(), true, List.of(), List.of()));
            // 결제 뒤 취소(#354)면 배송 건 · 주문상품을 해당 없음으로 나열한다(어느 판매자 · 상품이 취소됐는지 보이게). 결제 전 취소는 []
            boolean paid = order.getPaidAt() != null;
            List<DeliveryDetail> notShipped = paid ? notApplicable(deliveries, sellerNames) : List.of();
            List<ItemDetail> notConfirmed = paid
                ? order.getItems().stream().map(OrderTimeline::notApplicable).toList() : List.of();
            steps.add(new Step(StepName.SHIPPED, StepStatus.NOT_APPLICABLE, null, false, notShipped, List.of()));
            steps.add(new Step(StepName.DELIVERED, StepStatus.NOT_APPLICABLE, null, false, notShipped, List.of()));
            steps.add(new Step(StepName.CONFIRMED, StepStatus.NOT_APPLICABLE, null, false, List.of(), notConfirmed));
        } else {
            long sellerCount = order.getItems().stream()
                .map(OrderItem::getProduct)
                .filter(Objects::nonNull)
                .map(OrderItem.ProductSnapshot::sellerId)
                .distinct()
                .count();
            steps.add(deliveryStep(StepName.SHIPPED,
                deliveryDetails(deliveries, sellerNames, Delivery::shippedAt), sellerCount));
            steps.add(deliveryStep(StepName.DELIVERED,
                deliveryDetails(deliveries, sellerNames, Delivery::deliveredAt), sellerCount));
            steps.add(confirmedStep(order.getItems().stream().map(OrderTimeline::itemDetail).toList()));
        }
        return new OrderTimeline(
            order.getId(),
            order.getStatus().name(),
            currentStep(steps),
            order.getCancelReason() == null ? null : order.getCancelReason().name(),
            order.getStatus() == Order.Status.PENDING_PAYMENT ? order.getPaymentDueAt() : null,
            List.copyOf(steps));
    }

    private static Step paymentRequested(Order order, boolean cancelled) {
        if (order.getPaymentRequestedAt() != null) {
            return done(StepName.PAYMENT_REQUESTED, order.getPaymentRequestedAt());
        }
        return simple(StepName.PAYMENT_REQUESTED, cancelled ? StepStatus.NOT_APPLICABLE : StepStatus.WAITING);
    }

    private static List<DeliveryDetail> deliveryDetails(List<Delivery> deliveries, Map<Long, String> sellerNames,
        Function<Delivery, LocalDateTime> doneAt) {
        return deliveries.stream()
            .map(delivery -> new DeliveryDetail(delivery.deliveryId(), delivery.sellerId(),
                sellerNames.get(delivery.sellerId()), statusOf(doneAt.apply(delivery)), doneAt.apply(delivery)))
            .toList();
    }

    private static List<DeliveryDetail> notApplicable(List<Delivery> deliveries, Map<Long, String> sellerNames) {
        return deliveries.stream()
            .map(delivery -> new DeliveryDetail(delivery.deliveryId(), delivery.sellerId(),
                sellerNames.get(delivery.sellerId()), StepStatus.NOT_APPLICABLE, null))
            .toList();
    }

    private static ItemDetail notApplicable(OrderItem item) {
        OrderItem.ProductSnapshot product = item.getProduct();
        return new ItemDetail(item.getId(), product == null ? null : product.productName(), StepStatus.NOT_APPLICABLE,
            null, null);
    }

    private static ItemDetail itemDetail(OrderItem item) {
        OrderItem.ProductSnapshot product = item.getProduct();
        return new ItemDetail(item.getId(), product == null ? null : product.productName(),
            statusOf(item.getConfirmedAt()), item.getConfirmedAt(),
            item.getConfirmType() == null ? null : item.getConfirmType().name());
    }

    private static StepStatus statusOf(LocalDateTime doneAt) {
        return doneAt == null ? StepStatus.WAITING : StepStatus.DONE;
    }

    /**
     * 배송은 주문상품의 판매자 수만큼 있어야 완료로 본다: 배송 건 생성을 일부 놓친 주문은 DONE으로 올리지 않는다.
     */
    private static Step deliveryStep(StepName name, List<DeliveryDetail> details, long sellerCount) {
        Progress progress = Progress.of(details.stream().map(DeliveryDetail::at).toList(), sellerCount);
        return new Step(name, progress.status(), progress.at(), false, details, List.of());
    }

    private static Step confirmedStep(List<ItemDetail> details) {
        Progress progress = Progress.of(details.stream().map(ItemDetail::at).toList(), details.size());
        return new Step(StepName.CONFIRMED, progress.status(), progress.at(), false, List.of(), details);
    }

    /**
     * 건별 완료 시각(완료 전이면 null)으로 단계 상태를 정한다. 모두 완료이고 건수가 기대 건수 이상이면 DONE(가장 늦은 시각),
     * 일부만 완료면 PARTIAL, 하나도 없으면 WAITING.
     */
    private record Progress(StepStatus status, LocalDateTime at) {

        static Progress of(List<LocalDateTime> doneAts, long expectedCount) {
            List<LocalDateTime> done = doneAts.stream().filter(Objects::nonNull).toList();
            if (!done.isEmpty() && done.size() == doneAts.size() && doneAts.size() >= expectedCount) {
                return new Progress(StepStatus.DONE, done.stream().max(Comparator.naturalOrder()).orElseThrow());
            }
            return new Progress(done.isEmpty() ? StepStatus.WAITING : StepStatus.PARTIAL, null);
        }
    }

    private static StepName currentStep(List<Step> steps) {
        StepName current = null;
        for (Step step : steps) {
            if (step.status() == StepStatus.DONE || step.status() == StepStatus.PARTIAL) {
                current = step.step();
            }
        }
        return Objects.requireNonNull(current);
    }

    private static Step done(StepName name, LocalDateTime at) {
        return new Step(name, StepStatus.DONE, at, false, List.of(), List.of());
    }

    private static Step simple(StepName name, StepStatus status) {
        return new Step(name, status, null, false, List.of(), List.of());
    }
}
