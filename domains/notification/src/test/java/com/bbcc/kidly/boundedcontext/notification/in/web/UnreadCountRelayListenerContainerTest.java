package com.bbcc.kidly.boundedcontext.notification.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.net.ServerSocket;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

/**
 * Redis가 없을 때의 구독 컨테이너. Redis를 띄웠다 내리는 경우는 컨테이너를 공유하는 테스트에 영향을 줘 여기서 다루지 않는다.
 */
class UnreadCountRelayListenerContainerTest {

    private LettuceConnectionFactory connectionFactory;

    @AfterEach
    void tearDown() {
        connectionFactory.destroy();
    }

    @Test
    @DisplayName("Redis가 없어도 기동을 막지 않고, 실행 중 · 구독 없음 상태로 남는다")
    void startWithoutRedis() throws Exception {
        var container = containerWithoutRedis();

        container.start();

        assertThat(container.isRunning()).isTrue();
        assertThat(container.isListening()).isFalse();
        container.stop();
    }

    @Test
    @DisplayName("종료된 뒤에는 다시 구독을 시작하지 않는다")
    void doNotResubscribeAfterStop() throws Exception {
        var container = containerWithoutRedis();
        container.start();
        container.stop();

        container.resubscribe();

        assertThat(container.isRunning()).isFalse();
    }

    private UnreadCountRelayListenerContainer containerWithoutRedis() throws Exception {
        int closedPort;
        try (var socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        connectionFactory = new LettuceConnectionFactory(new RedisStandaloneConfiguration("localhost", closedPort));
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        var container = new UnreadCountRelayListenerContainer(connectionFactory, mock(UnreadCountRelay.class), "test");
        container.afterPropertiesSet();
        return container;
    }
}
