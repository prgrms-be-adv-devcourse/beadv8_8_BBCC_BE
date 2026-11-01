package com.bbcc.kidly.boundedcontext.order.in.event;

import com.bbcc.kidly.boundedcontext.order.in.config.OrderKafkaListenerConfig;
import com.bbcc.kidly.shared.product.event.ProductChangedEvent;
import com.bbcc.kidly.shared.product.event.StockDeductedEvent;
import com.bbcc.kidly.shared.product.event.StockDeductionFailedEvent;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * kidly.product.events에서 product.changed · stock.deducted · stock.deduction-failed를 받아 지금 처리를 그대로 부른다(#541).
 * 같은 토픽 · 같은 그룹에 클래스가 둘이면 메시지를 나눠 가지므로 상품 · 재고를 이 클래스 하나에서 받는다.
 * <ul>
 *   <li>처리는 각 처리 메서드가 여는 트랜잭션에서 커밋까지 끝난다. 이 메서드는 트랜잭션을 열지 않는다
 *       (실패 보관이 리스너 트랜잭션에 합류하면 정상 반환해도 커밋 때 rollback-only 예외가 난다)</li>
 *   <li>재고 2종: 처리 실패를 주문 자체 보관(order_event_failure)에 남겼으면 처리 완료로 보고 정상 반환한다(관리자가 재처리).
 *       보관까지 실패했으면 예외를 그대로 던져 재시도 → DLT로 보낸다</li>
 *   <li>상품: 보관이 없으므로 예외를 그대로 던진다(재시도 → DLT)</li>
 *   <li>본문을 읽지 못하면(모르는 enum 값 · eventType 헤더 없음) 재시도 없이 DLT로 간다(OrderKafkaListenerConfig)</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class ProductEventKafkaListener {

    static final String ID = "orderProductEventKafkaListener";

    private final JsonMapper jsonMapper;
    private final ProductEventListener productEventListener;
    private final StockEventListener stockEventListener;

    @KafkaListener(id = ID, topics = OrderKafkaListenerConfig.PRODUCT_EVENTS, groupId = OrderKafkaListenerConfig.GROUP,
        containerFactory = OrderKafkaListenerConfig.FACTORY)
    public void on(ConsumerRecord<String, String> record) {
        switch (OrderKafkaMessages.eventType(record)) {
            case "product.changed" -> productEventListener.onProductChanged(read(record, ProductChangedEvent.class));
            case "stock.deducted" -> {
                StockDeductedEvent event = read(record, StockDeductedEvent.class);
                completeIfHandled(() -> stockEventListener.onStockDeducted(event));
            }
            case "stock.deduction-failed" -> {
                StockDeductionFailedEvent event = read(record, StockDeductionFailedEvent.class);
                completeIfHandled(() -> stockEventListener.onStockDeductionFailed(event));
            }
            default -> {
                // 주문이 받지 않는 종류
            }
        }
    }

    private <T> T read(ConsumerRecord<String, String> record, Class<T> type) {
        return jsonMapper.readValue(record.value(), type);
    }

    private static void completeIfHandled(Runnable process) {
        try {
            process.run();
        } catch (RuntimeException e) {
            if (!EventFailureKeeper.isHandled(e)) {
                throw e;
            }
            EventHandOffs.noteHandedOff();
        }
    }
}
