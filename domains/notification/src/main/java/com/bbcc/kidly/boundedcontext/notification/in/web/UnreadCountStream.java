package com.bbcc.kidly.boundedcontext.notification.in.web;

import com.bbcc.kidly.boundedcontext.notification.app.InboxChanged;
import com.bbcc.kidly.boundedcontext.notification.app.InboxNotificationService;
import com.bbcc.kidly.boundedcontext.notification.in.web.dto.UnreadCountResponse;
import com.bbcc.kidly.shared.auth.PrincipalId;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 안 읽은 알림 수 SSE. 연결하면 지금 안 읽은 수를 보내고, 수가 바뀔 때마다(InboxChanged, 커밋 뒤) 새 수를 보낸다.
 * 연결은 이 서버 메모리에만 있다. 알림 서비스는 Pod 1대라(deploy/k3s/k3s.py SINGLE_INSTANCE) 다른 서버로 전달하지 않는다.
 */
@Component
@RequiredArgsConstructor
public class UnreadCountStream {

    static final String EVENT = "unread-count";

    // 액세스 토큰 유효 시간(30분, auth.yml access-token-validity)과 같다. 끊기면 화면이 새 토큰으로 다시 연결한다
    private static final long TIMEOUT_MILLIS = 30 * 60 * 1000L;

    private final InboxNotificationService inboxNotificationService;
    private final Map<PrincipalId, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter open(PrincipalId principal, HttpServletResponse response) {
        // nginx가 응답을 모았다가 한꺼번에 보내지 않고, 이벤트를 받는 대로 흘려보내게 한다
        response.setHeader("X-Accel-Buffering", "no");
        var emitter = new SseEmitter(TIMEOUT_MILLIS);
        emitters.computeIfAbsent(principal, key -> ConcurrentHashMap.newKeySet()).add(emitter);
        emitter.onCompletion(() -> remove(principal, emitter));
        emitter.onTimeout(emitter::complete);
        emitter.onError(error -> remove(principal, emitter));
        send(emitter, SseEmitter.event().name(EVENT).data(count(principal), MediaType.APPLICATION_JSON));
        return emitter;
    }

    @TransactionalEventListener
    public void onChanged(InboxChanged event) {
        var open = emitters.get(event.principal());
        if (open == null || open.isEmpty()) {
            return;
        }
        var count = count(event.principal());
        open.forEach(emitter -> send(emitter, SseEmitter.event().name(EVENT).data(count, MediaType.APPLICATION_JSON)));
    }

    // nginx(proxy_read_timeout 기본 60초) · Gateway(response-timeout 55초)는 응답 사이 간격으로 끊으므로 그보다 짧게 주석 한 줄을 보낸다
    @Scheduled(fixedRate = 30_000)
    public void heartbeat() {
        emitters.values().forEach(open -> open.forEach(emitter -> send(emitter, SseEmitter.event().comment("ping"))));
    }

    // 열린 연결이 남아 있으면 graceful shutdown이 끝까지(30초) 기다리므로, 종료가 시작되면 먼저 닫는다. 화면은 다시 연결한다
    @EventListener(ContextClosedEvent.class)
    public void closeAll() {
        emitters.values().forEach(open -> open.forEach(SseEmitter::complete));
    }

    private UnreadCountResponse count(PrincipalId principal) {
        return new UnreadCountResponse(inboxNotificationService.countUnread(principal));
    }

    private void remove(PrincipalId principal, SseEmitter emitter) {
        emitters.computeIfPresent(principal, (key, open) -> {
            open.remove(emitter);
            return open.isEmpty() ? null : open;
        });
    }

    // 화면이 연결을 끊었으면(IOException) 오류로 닫아 onError가 목록에서 뺀다. 이미 닫힌 연결(IllegalStateException)은 버린다
    private static void send(SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (IOException e) {
            emitter.completeWithError(e);
        } catch (IllegalStateException e) {
            return;
        }
    }
}
