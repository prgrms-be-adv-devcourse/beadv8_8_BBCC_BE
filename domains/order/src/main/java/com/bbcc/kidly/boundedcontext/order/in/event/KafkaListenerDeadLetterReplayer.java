package com.bbcc.kidly.boundedcontext.order.in.event;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.bbcc.kidly.boundedcontext.order.app.OrderLogContext;
import com.bbcc.kidly.boundedcontext.order.app.deadletter.UnknownDeadLetterSourceException;
import com.bbcc.kidly.boundedcontext.order.app.port.DeadLetterReplayer;
import com.bbcc.kidly.boundedcontext.order.in.config.OrderKafkaListenerConfig;
import java.util.Optional;
import java.util.function.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.record.TimestampType;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 저장한 DLT 메시지로 원래 Kafka 메시지를 다시 만들어 원래 토픽의 리스너에 그대로 넘긴다(#601).
 * 읽기 · 종류 분기 · 처리 완료 표시(주문 자체 보관) · 멱등 처리가 Kafka로 받았을 때와 같다. 원래 토픽에 다시 넣지 않는다.
 * 자기 토픽(kidly.order.events)의 리스너는 조르기 컨텍스트에 있고 주문은 조르기를 참조하지 않으므로(컨텍스트 규칙),
 * 그 리스너가 내놓은 {@code Consumer<ConsumerRecord>} 빈으로 넘긴다(#999). 조르기 스위치가 꺼져 있으면 빈이 없어 모르는 원래 토픽으로 본다.
 */
@Component
class KafkaListenerDeadLetterReplayer implements DeadLetterReplayer {

    private final ProductEventKafkaListener productListener;
    private final SellerEventKafkaListener sellerListener;
    private final ChildProfileEventKafkaListener childProfileListener;
    private final PaymentEventKafkaListener paymentListener;
    private final AuthEventKafkaListener authListener;
    private final MemberEventKafkaListener memberListener;
    private final ObjectProvider<Consumer<ConsumerRecord<String, String>>> orderEventsListener;

    /**
     * 자기 토픽 리스너({@code Consumer<ConsumerRecord>} 빈)는 0개(조르기 꺼짐) 또는 1개여야 한다. 둘 이상이면 어느 쪽으로 넘길지 모르므로
     * 기동을 멈춘다(조용히 '모르는 토픽'으로 떨어지지 않게).
     */
    KafkaListenerDeadLetterReplayer(ProductEventKafkaListener productListener, SellerEventKafkaListener sellerListener,
        ChildProfileEventKafkaListener childProfileListener, PaymentEventKafkaListener paymentListener,
        AuthEventKafkaListener authListener, MemberEventKafkaListener memberListener,
        ObjectProvider<Consumer<ConsumerRecord<String, String>>> orderEventsListener) {
        long candidates = orderEventsListener.stream().count();
        if (candidates > 1) {
            throw new IllegalStateException("자기 토픽(" + OrderKafkaListenerConfig.ORDER_EVENTS
                + ") DLT 다시 처리 대상 리스너가 " + candidates + "개입니다. 하나만 둔다");
        }
        this.productListener = productListener;
        this.sellerListener = sellerListener;
        this.childProfileListener = childProfileListener;
        this.paymentListener = paymentListener;
        this.authListener = authListener;
        this.memberListener = memberListener;
        this.orderEventsListener = orderEventsListener;
    }

    @Override
    public Outcome replay(Message message) {
        ConsumerRecord<String, String> record = record(message);
        // 관리자 요청에서 리스너를 직접 부르므로 Kafka 인터셉터를 지나지 않는다. 같은 추적 값을 여기서 둔다(traceId는 요청 값 유지)
        try (var ignored = OrderLogContext.open(message.eventId(), message.eventType(), message.messageKey())) {
            return replayWatched(message, record);
        }
    }

    private Outcome replayWatched(Message message, ConsumerRecord<String, String> record) {
        boolean handedOff = EventHandOffs.watch(() -> {
            switch (message.sourceTopic()) {
                case OrderKafkaListenerConfig.PRODUCT_EVENTS -> productListener.on(record);
                case OrderKafkaListenerConfig.SELLER_EVENTS -> sellerListener.on(record);
                case OrderKafkaListenerConfig.CHILDPROFILE_EVENTS -> childProfileListener.on(record);
                case OrderKafkaListenerConfig.PAYMENT_EVENTS -> paymentListener.on(record);
                case OrderKafkaListenerConfig.AUTH_EVENTS -> authListener.on(record);
                case OrderKafkaListenerConfig.MEMBER_EVENTS -> memberListener.on(record);
                case OrderKafkaListenerConfig.ORDER_EVENTS -> orderEventsListener(message).accept(record);
                default -> throw new UnknownDeadLetterSourceException(message.sourceTopic());
            }
        });
        return new Outcome(handedOff);
    }

    private Consumer<ConsumerRecord<String, String>> orderEventsListener(Message message) {
        Consumer<ConsumerRecord<String, String>> listener = orderEventsListener.getIfAvailable();
        if (listener == null) {
            throw new UnknownDeadLetterSourceException(message.sourceTopic());
        }
        return listener;
    }

    // 원래 위치 · 키 · 본문, 원래 헤더 eventType · eventId(없던 헤더는 넣지 않는다)
    private static ConsumerRecord<String, String> record(Message message) {
        RecordHeaders headers = new RecordHeaders();
        if (message.eventType() != null) {
            headers.add(OrderKafkaMessages.EVENT_TYPE, message.eventType().getBytes(UTF_8));
        }
        if (message.eventId() != null) {
            headers.add(OrderKafkaMessages.EVENT_ID, message.eventId().getBytes(UTF_8));
        }
        return new ConsumerRecord<>(message.sourceTopic(), message.sourcePartition(), message.sourceOffset(),
            ConsumerRecord.NO_TIMESTAMP, TimestampType.NO_TIMESTAMP_TYPE, ConsumerRecord.NULL_SIZE,
            ConsumerRecord.NULL_SIZE, message.messageKey(), message.payload(), headers, Optional.empty());
    }
}
