package com.bbcc.kidly.boundedcontext.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.notification.app.InboxNotificationService;
import com.bbcc.kidly.boundedcontext.notification.domain.InboxNotificationInfo;
import com.bbcc.kidly.boundedcontext.notification.domain.InboxRecipient;
import com.bbcc.kidly.boundedcontext.notification.domain.NotificationType;
import com.bbcc.kidly.shared.auth.MemberHeaders;
import com.bbcc.kidly.shared.auth.PrincipalHeaders;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * 안 읽은 알림 수 SSE. 연결하면 지금 수를, 알림이 들어오거나 읽으면 새 수를 받는다. 다른 받는 사람의 변화는 받지 않는다.
 */
@FunctionalTest
class UnreadCountStreamApiTest {

    private static final long ME = 1L;

    @LocalServerPort
    private int port;

    @Autowired
    private InboxNotificationService inboxNotificationService;

    @Test
    @DisplayName("회원은 연결하면 지금 안 읽은 수를 받고, 알림이 들어오거나 모두 읽으면 바뀐 수를 받는다")
    void memberReceivesUnreadCountChanges() throws Exception {
        add(InboxRecipient.member(ME), "k1");
        try (var stream = open("/api/v1/notifications/stream", MemberHeaders.MEMBER_ID, String.valueOf(ME))) {
            assertThat(stream.nextData()).isEqualTo("{\"count\":1}");

            add(InboxRecipient.child(ME), "다른 받는 사람");
            add(InboxRecipient.member(ME), "k2");
            assertThat(stream.nextData()).isEqualTo("{\"count\":2}");

            inboxNotificationService.readAll(PrincipalId.member(ME));
            assertThat(stream.nextData()).isEqualTo("{\"count\":0}");
        }
    }

    @Test
    @DisplayName("자녀는 자녀 알림함의 안 읽은 수를 받는다")
    void childReceivesUnreadCountChanges() throws Exception {
        try (var stream = open("/api/v1/child/notifications/stream",
            PrincipalHeaders.CHILD_ACCOUNT_ID, String.valueOf(ME), PrincipalHeaders.CHILD_GUARDIAN_ID, "99")) {
            assertThat(stream.nextData()).isEqualTo("{\"count\":0}");

            add(InboxRecipient.child(ME), "k1");
            assertThat(stream.nextData()).isEqualTo("{\"count\":1}");
        }
    }

    private void add(InboxRecipient recipient, String dedupKey) {
        inboxNotificationService.add(
            new InboxNotificationInfo(recipient, NotificationType.WISH_REJECTED, "제목", "본문", null, dedupKey));
    }

    private Stream open(String path, String... headers) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Accept", "text/event-stream").headers(headers).GET().build();
        var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofInputStream());
        assertThat(response.statusCode()).isEqualTo(200);
        return new Stream(new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8)));
    }

    private record Stream(BufferedReader reader) implements AutoCloseable {

        // 다음 unread-count 이벤트의 data. 주석(:ping)은 건너뛴다. 5초 안에 오지 않으면 실패한다
        String nextData() throws Exception {
            return CompletableFuture.supplyAsync(this::readData).get(5, TimeUnit.SECONDS);
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
