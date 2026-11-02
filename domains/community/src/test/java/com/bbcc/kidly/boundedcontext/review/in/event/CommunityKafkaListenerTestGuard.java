package com.bbcc.kidly.boundedcontext.review.in.event;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.SmartLifecycle;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.stereotype.Component;

/**
 * 커뮤니티 Kafka 리스너가 테스트가 직접 켤 때만 돌게 한다(주문 모듈 테스트 가드의 복사본). 테스트 클래스패스의 컴포넌트 스캔에 잡혀
 * 모든 통합 · 기능 테스트 컨텍스트에 똑같이 등록된다. 설정을 더하지 않으므로 컨텍스트 수는 그대로다.
 * <p>
 * 이유: 테스트 컨텍스트 캐시는 쓰지 않는 컨텍스트를 일시 정지했다가 다시 쓸 때 Lifecycle을 다시 시작하고, 그때
 * KafkaListenerEndpointRegistry.start()는 autoStartup과 상관없이 등록된 모든 리스너 컨테이너를 시작한다.
 * 그러면 리스너가 처음부터(earliest) 다른 테스트가 남긴 메시지를 처리해 다른 테스트의 복사본을 덮는다.
 * 첫 기동 때(autoStartup=false라 아직 꺼진 상태) 커뮤니티 리스너를 레지스트리에서 영구히 빼 두고 이 빈이 들고 있는다.
 * 받기 테스트는 {@link #listener(String)}로 꺼내 그 테스트 안에서 start · stop한다. 테스트가 켠 채로 컨텍스트가 멈추면 여기서 멈춘다.
 */
@Component
public class CommunityKafkaListenerTestGuard implements SmartLifecycle {

    static final List<String> COMMUNITY_LISTENERS = List.of(CommunityProductEventKafkaListener.ID,
        CommunityOrderEventKafkaListener.ID);

    private final KafkaListenerEndpointRegistry registry;
    private final Map<String, MessageListenerContainer> listeners = new ConcurrentHashMap<>();
    private volatile boolean running;

    public CommunityKafkaListenerTestGuard(KafkaListenerEndpointRegistry registry) {
        this.registry = registry;
    }

    /**
     * 레지스트리에서 뺀 커뮤니티 리스너. 받기 테스트가 직접 start · stop한다.
     */
    public MessageListenerContainer listener(String id) {
        MessageListenerContainer listener = listeners.get(id);
        if (listener == null) {
            throw new IllegalArgumentException("커뮤니티 Kafka 리스너가 아님: " + id);
        }
        return listener;
    }

    @Override
    public void start() {
        for (String id : COMMUNITY_LISTENERS) {
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
