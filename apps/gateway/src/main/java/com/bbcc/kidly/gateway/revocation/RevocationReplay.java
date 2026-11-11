package com.bbcc.kidly.gateway.revocation;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.event.ConsumerStartingEvent;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.event.ListenerContainerIdleEvent;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.stereotype.Component;

/**
 * 무효화 목록을 다시 다 읽었는지(#1176). 다 읽기 전에는 Gateway 가 요청을 받지 않는다(readiness, RevocationAwareApplicationAvailability).
 * 목록이 메모리라 Gateway 가 뜰 때마다 31분 전부터 다시 읽는데(AuthTokensRevokedListener), 그 전에 요청을 받으면 무효화된 토큰이 통과한다.
 * 롤링 배포에서는 새 Pod 가 Ready 가 되는 순간 요청을 받으므로 배포마다 이 공백이 생겼다.
 * <ul>
 *   <li>다 읽음: 무효화 리스너가 파티션을 받은 뒤 더 읽을 메시지가 없을 때(컨테이너 idle 이벤트, GatewayKafkaListenerConfig 의 간격)</li>
 *   <li>리스너가 다시 시작하면(ConsumerStartingEvent) 다시 읽으므로 처음부터 기다린다. 한 번 다 읽은 뒤의 재조정(rebalance)으로는
 *   다시 막지 않는다. 그동안 받은 무효화는 메모리에 그대로 있다</li>
 *   <li>끝없이 막지 않는다: 리스너가 시작하거나 앱이 뜬 뒤 TIMEOUT 안에 다 읽지 못하면 WARN 을 남기고 요청을 받는다.
 *   앱이 뜰 때 리스너가 돌지 않으면 기다리지 않는다
 *   (브로커 장애로 Gateway 전체가 멈추지 않게. k3s 는 새 Pod 를 최대 420초 기다린다)</li>
 * </ul>
 * 로그에는 파티션 수 · 시간만 남긴다.
 */
@Slf4j
@Component
public class RevocationReplay implements DisposableBean {

    static final Duration TIMEOUT = Duration.ofSeconds(60);

    private final Duration timeout;
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(
        Thread.ofPlatform().name("gateway-revocation-replay").daemon().factory());
    // 리스너가 다시 시작하면 늘어난다. 앞 시작의 시간 상한이 뒤 시작을 Ready 로 만들지 않게 한다
    private final AtomicLong generation = new AtomicLong();
    private volatile boolean caughtUp;

    public RevocationReplay() {
        this(TIMEOUT);
    }

    RevocationReplay(Duration timeout) {
        this.timeout = timeout;
    }

    public boolean caughtUp() {
        return caughtUp;
    }

    // 뜰 때 리스너가 돌지 않으면(테스트의 auto-startup: false 등) 기다릴 것이 없다. 나중에 시작하면 그때부터 기다린다
    @EventListener
    void onApplicationReady(ApplicationReadyEvent event) {
        var container = event.getApplicationContext().getBean(KafkaListenerEndpointRegistry.class)
            .getListenerContainer(AuthTokensRevokedListener.LISTENER_ID);
        if (container == null || !container.isRunning()) {
            caughtUp = true;
            log.info("토큰 무효화 리스너가 돌지 않아 목록을 기다리지 않고 요청을 받습니다");
            return;
        }
        arm(generation.get());
    }

    @EventListener
    void onConsumerStarting(ConsumerStartingEvent event) {
        if (!isRevocationListener(event.getContainer(MessageListenerContainer.class).getListenerId())) {
            return;
        }
        caughtUp = false;
        arm(generation.incrementAndGet());
    }

    @EventListener
    void onIdle(ListenerContainerIdleEvent event) {
        // 파티션을 받기 전의 idle 은 아직 아무것도 읽지 않은 것이다
        if (!isRevocationListener(event.getListenerId()) || event.getTopicPartitions() == null
            || event.getTopicPartitions().isEmpty() || caughtUp) {
            return;
        }
        caughtUp = true;
        log.info("토큰 무효화 목록을 다 읽어 요청을 받습니다. partitions={}", event.getTopicPartitions().size());
    }

    private void arm(long armedGeneration) {
        timer.schedule(() -> expire(armedGeneration), timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    private void expire(long armedGeneration) {
        if (caughtUp || generation.get() != armedGeneration) {
            return;
        }
        caughtUp = true;
        log.warn("토큰 무효화 목록을 {}초 안에 다 읽지 못했지만 요청을 받습니다. 그 사이 무효화된 토큰이 통과할 수 있습니다",
            timeout.toSeconds());
    }

    // ConcurrentMessageListenerContainer 의 자식은 "<id>-0" 처럼 번호가 붙는다
    private static boolean isRevocationListener(String listenerId) {
        return listenerId != null && listenerId.startsWith(AuthTokensRevokedListener.LISTENER_ID);
    }

    @Override
    public void destroy() {
        timer.shutdownNow();
    }
}
