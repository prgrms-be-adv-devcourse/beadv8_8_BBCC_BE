package com.bbcc.kidly.boundedcontext.notification.in.web;

import com.bbcc.kidly.boundedcontext.notification.app.InboxChanged;
import com.bbcc.kidly.shared.auth.PrincipalId;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 롤링 배포로 Pod가 겹치는 동안 다른 Pod의 연결도 새 수를 받게 Redis 채널로 받는 사람만 알린다.
 * 수는 받는 Pod가 다시 세므로 순서가 바뀌거나 메시지를 잃어도 다음 변경 때 맞는다.
 * Redis가 죽어도 알림 저장 · 이 Pod의 SSE는 그대로고 WARN만 남긴다.
 */
@Slf4j
@Component
public class UnreadCountRelay implements MessageListener {

    private final String instanceId = UUID.randomUUID().toString();
    private final StringRedisTemplate redisTemplate;
    private final UnreadCountStream unreadCountStream;
    private final String channel;

    public UnreadCountRelay(StringRedisTemplate redisTemplate, UnreadCountStream unreadCountStream,
        @Value("${kidly.notification.unread-count-channel}") String channel) {
        this.redisTemplate = redisTemplate;
        this.unreadCountStream = unreadCountStream;
        this.channel = channel;
    }

    @TransactionalEventListener
    public void onChanged(InboxChanged event) {
        try {
            redisTemplate.convertAndSend(channel, instanceId + " " + event.principal().toSubject());
        } catch (RuntimeException e) {
            log.warn("안 읽은 알림 수 변경을 다른 Pod에 알리지 못했습니다. reason={}", e.getMessage());
        }
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            var parts = new String(message.getBody(), StandardCharsets.UTF_8).split(" ", 2);
            if (instanceId.equals(parts[0])) {
                return;
            }
            unreadCountStream.push(PrincipalId.parseSubject(parts[1]));
        } catch (RuntimeException e) {
            log.warn("다른 Pod의 안 읽은 알림 수 변경을 보내지 못했습니다. reason={}", e.getMessage());
        }
    }
}
