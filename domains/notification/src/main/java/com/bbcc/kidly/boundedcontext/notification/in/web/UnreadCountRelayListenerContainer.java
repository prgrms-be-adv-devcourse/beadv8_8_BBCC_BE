package com.bbcc.kidly.boundedcontext.notification.in.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * UnreadCountRelay의 Redis 구독. Redis가 없어도 앱은 뜨고, Redis가 돌아오면 다시 구독한다.
 * 기본 컨테이너는 기동 때 Redis에 붙지 못하면 start()가 예외를 던져 기동이 실패하고, 그 뒤 다시 구독하지 않는다
 * (spring-data-redis 4.1.1). 붙은 뒤 끊기면 스스로 다시 붙지만 그동안 몇 초마다 ERROR(Slack 장애 채널로 감)를 남긴다.
 * 그래서 이 클래스 로그는 끈다(notification.yml). Redis 장애는 UnreadCountRelay의 발행 실패 WARN으로 보인다.
 */
@Component
public class UnreadCountRelayListenerContainer extends RedisMessageListenerContainer {

    public UnreadCountRelayListenerContainer(RedisConnectionFactory connectionFactory, UnreadCountRelay relay,
        @Value("${kidly.notification.unread-count-channel}") String channel) {
        setConnectionFactory(connectionFactory);
        addMessageListener(relay, new ChannelTopic(channel));
    }

    @Override
    public void start() {
        try {
            super.start();
        } catch (RuntimeException e) {
            // Redis에 붙지 못했다. resubscribe()가 다시 시도한다
        }
    }

    // 기동 때 붙지 못해 구독이 없으면 다시 시작한다. 종료 중(isRunning false)에는 건드리지 않는다
    @Scheduled(fixedDelay = 30_000)
    public void resubscribe() {
        if (isRunning() && !isListening()) {
            stop();
            start();
        }
    }
}
