package com.bbcc.kidly.boundedcontext.order.in.event;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.SmartLifecycle;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.stereotype.Component;

/**
 * 주문 Kafka 리스너 5개(상품 · 판매자 · 자녀 #541, 결제 #594, DLT #601)가 테스트가 직접 켤 때만 돌게 한다. 테스트 클래스패스의 컴포넌트 스캔에 잡혀 모든 통합 · 기능 테스트
 * 컨텍스트에 똑같이 등록된다(OrderFlowEventRecorder와 같다). 설정을 더하지 않으므로 컨텍스트 수는 그대로다.
 * <p>
 * 이유: 테스트 컨텍스트 캐시는 쓰지 않는 컨텍스트를 일시 정지했다가 다시 쓸 때 Lifecycle을 다시 시작하고, 그때
 * KafkaListenerEndpointRegistry.start()는 autoStartup과 상관없이 등록된 모든 리스너 컨테이너를 시작한다(#541-2, 공용 문제).
 * 그러면 주문 리스너가 처음부터(earliest) 다른 테스트가 남긴 상품 · 판매자 · 자녀 메시지를 처리해 다른 테스트의 복사본을 덮는다.
 * 시작된 뒤 멈추면 멈추기까지(최대 poll 한 번) 메시지를 읽을 수 있으므로, 첫 기동 때(autoStartup=false라 아직 꺼진 상태)
 * 주문 리스너를 레지스트리에서 영구히 빼 두고 이 빈이 들고 있는다. 그러면 재시작 때 registry.start()가 순회하는 대상에서
 * 빠져 시작되지 않는다. 이 빈은 레지스트리를 주입받으므로 Lifecycle 시작은 레지스트리가 먼저다(phase로 순서를 정하지 않는다).
 * 다른 도메인 리스너는 건드리지 않는다.
 * 받기 테스트는 {@link #listener(String)}로 꺼내 그 테스트 안에서 start · stop한다. 테스트가 켠 채로 컨텍스트가 멈추면 여기서 멈춘다.
 */
@Component
public class OrderKafkaListenerTestGuard implements SmartLifecycle {

    static final List<String> ORDER_LISTENERS = List.of(ProductEventKafkaListener.ID, SellerEventKafkaListener.ID,
        ChildProfileEventKafkaListener.ID, PaymentEventKafkaListener.ID, OrderDeadLetterKafkaListener.ID);

    private final KafkaListenerEndpointRegistry registry;
    private final Map<String, MessageListenerContainer> listeners = new ConcurrentHashMap<>();
    private volatile boolean running;

    public OrderKafkaListenerTestGuard(KafkaListenerEndpointRegistry registry) {
        this.registry = registry;
    }

    /**
     * 레지스트리에서 뺀 주문 리스너. 받기 테스트가 직접 start · stop한다.
     */
    public MessageListenerContainer listener(String id) {
        MessageListenerContainer listener = listeners.get(id);
        if (listener == null) {
            throw new IllegalArgumentException("주문 Kafka 리스너가 아님: " + id);
        }
        return listener;
    }

    @Override
    public void start() {
        for (String id : ORDER_LISTENERS) {
            MessageListenerContainer listener = registry.unregisterListenerContainer(id);
            if (listener != null) {
                listeners.put(id, listener);
            }
        }
        running = true;
    }

    @Override
    public void stop() {
        listeners.values().stream().filter(MessageListenerContainer::isRunning).forEach(MessageListenerContainer::stop);
        running = false;
    }

    // 일시 정지 때 false가 되어야 다시 시작할 때 start()가 다시 불린다
    @Override
    public boolean isRunning() {
        return running;
    }
}
