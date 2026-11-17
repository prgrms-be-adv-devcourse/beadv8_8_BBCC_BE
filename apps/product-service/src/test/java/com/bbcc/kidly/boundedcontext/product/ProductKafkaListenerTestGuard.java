package com.bbcc.kidly.boundedcontext.product;

import com.bbcc.kidly.boundedcontext.product.in.inventory.ProductOrderKafkaListener;
import com.bbcc.kidly.boundedcontext.product.in.replica.ProductSellerKafkaListener;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.SmartLifecycle;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.stereotype.Component;

@Component
class ProductKafkaListenerTestGuard implements SmartLifecycle {

    private static final List<String> PRODUCT_LISTENERS = List.of(ProductSellerKafkaListener.ID,
        ProductOrderKafkaListener.ID);

    private final KafkaListenerEndpointRegistry registry;
    private final Map<String, MessageListenerContainer> listeners = new ConcurrentHashMap<>();
    private volatile boolean running;

    ProductKafkaListenerTestGuard(KafkaListenerEndpointRegistry registry) {
        this.registry = registry;
    }

    MessageListenerContainer listener(String id) {
        MessageListenerContainer listener = listeners.get(id);
        if (listener == null) {
            throw new IllegalArgumentException("상품 Kafka 리스너가 아님: " + id);
        }
        return listener;
    }

    @Override
    public void start() {
        for (String id : PRODUCT_LISTENERS) {
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
