package com.bbcc.kidly.boundedcontext.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.notification.app.InboxNotificationService;
import com.bbcc.kidly.boundedcontext.notification.domain.InboxNotification;
import com.bbcc.kidly.boundedcontext.notification.domain.InboxNotificationInfo;
import com.bbcc.kidly.boundedcontext.notification.domain.InboxNotificationRepository;
import com.bbcc.kidly.boundedcontext.notification.domain.InboxRecipient;
import com.bbcc.kidly.boundedcontext.notification.domain.NotificationType;
import com.bbcc.kidly.shared.auth.MemberHeaders;
import com.bbcc.kidly.shared.auth.PrincipalId;
import com.bbcc.kidly.support.FunctionalTest;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.transaction.support.TransactionTemplate;

@FunctionalTest
class UnreadCountRelayApiTest {

    private static final long ME = 1L;

    @LocalServerPort
    private int port;

    @Value("${kidly.notification.unread-count-channel}")
    private String channel;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private InboxNotificationRepository repository;

    @Autowired
    private InboxNotificationService inboxNotificationService;

    @Autowired
    private RedisConnectionFactory connectionFactory;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    @DisplayName("다른 Pod가 알린 변경을 받으면 이 Pod의 연결에 새 수를 보낸다")
    void receivesOtherPodChange() throws Exception {
        try (var stream = open()) {
            assertThat(stream.nextData().get(5, TimeUnit.SECONDS)).isEqualTo("{\"count\":0}");
            saveOnly("k1");
            redisTemplate.convertAndSend(channel, "other-pod " + PrincipalId.member(ME).toSubject());
            assertThat(stream.nextData().get(5, TimeUnit.SECONDS)).isEqualTo("{\"count\":1}");
        }
    }

    @Test
    @DisplayName("자기가 보낸 메시지는 무시한다")
    void ignoresOwnMessage() throws Exception {
        var messages = new LinkedBlockingQueue<String>();
        var container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener((message, pattern) ->
            messages.add(new String(message.getBody(), StandardCharsets.UTF_8)), new ChannelTopic(channel));
        container.afterPropertiesSet();
        container.start();
        try (var stream = open()) {
            assertThat(stream.nextData().get(5, TimeUnit.SECONDS)).isEqualTo("{\"count\":0}");
            inboxNotificationService.add(info("k1"));
            assertThat(stream.nextData().get(5, TimeUnit.SECONDS)).isEqualTo("{\"count\":1}");
            var ownMessage = messages.poll(5, TimeUnit.SECONDS);
            assertThat(ownMessage).isNotNull();
            saveOnly("k2");
            redisTemplate.convertAndSend(channel, ownMessage);
            var next = stream.nextData();
            assertThatThrownBy(() -> next.get(1, TimeUnit.SECONDS)).isInstanceOf(TimeoutException.class);
            redisTemplate.convertAndSend(channel, "other-pod " + PrincipalId.member(ME).toSubject());
            assertThat(next.get(5, TimeUnit.SECONDS)).isEqualTo("{\"count\":2}");
        } finally {
            container.stop();
            container.destroy();
        }
    }

    private void saveOnly(String dedupKey) {
        transactionTemplate.executeWithoutResult(status ->
            assertThat(repository.saveIfAbsent(InboxNotification.create(info(dedupKey)), LocalDateTime.now()))
                .isTrue());
    }

    private InboxNotificationInfo info(String dedupKey) {
        return new InboxNotificationInfo(InboxRecipient.member(ME), NotificationType.WISH_REJECTED,
            "제목", "본문", null, dedupKey);
    }

    private Stream open() throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/notifications/stream"))
            .header("Accept", "text/event-stream").header(MemberHeaders.MEMBER_ID, String.valueOf(ME)).GET().build();
        var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofInputStream());
        assertThat(response.statusCode()).isEqualTo(200);
        return new Stream(new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8)));
    }

    private record Stream(BufferedReader reader) implements AutoCloseable {

        CompletableFuture<String> nextData() {
            return CompletableFuture.supplyAsync(this::readData);
        }

        private String readData() {
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("data:")) {
                        return line.substring("data:".length());
                    }
                }
                return null;
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public void close() throws Exception {
            reader.close();
        }
    }
}
