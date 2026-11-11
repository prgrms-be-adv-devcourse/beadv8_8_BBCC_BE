package com.bbcc.kidly.boundedcontext.recommend.in.support;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 규칙 결과와 나중에 끝나는 AI 결과를 SSE 이벤트로 보낸다.
 * AI 결과가 이미 있으면(저장한 답) ai 이벤트만, 아니면 rule 이벤트를 먼저 보내고 AI 결과가 오면 ai 이벤트를 보낸 뒤 닫는다.
 * AI 결과를 얻지 못하면 rule 이벤트만 보낸 채 닫는다. 화면은 rule을 들고 있다가 ai 없이 닫히면 그것을 그린다.
 */
@Component
public class AiResultStream {

    // AI 공통 서비스가 AI 답을 최대 10초 기다리므로 연결은 그보다 길게 연다
    private static final long TIMEOUT_MILLIS = 15_000;

    public <T> SseEmitter open(HttpServletResponse response, T rule, CompletableFuture<Optional<T>> ai) {
        // nginx가 응답을 모았다가 한꺼번에 보내지 않고, 이벤트를 받는 대로 흘려보내게 한다
        response.setHeader("X-Accel-Buffering", "no");
        SseEmitter emitter = new SseEmitter(TIMEOUT_MILLIS);
        send(emitter, rule, ai);
        return emitter;
    }

    public <T> void send(SseEmitter emitter, T rule, CompletableFuture<Optional<T>> ai) {
        Optional<T> done = ai.getNow(null);
        if (done != null && done.isPresent()) {
            sendEvent(emitter, "ai", done.get());
            emitter.complete();
            return;
        }
        sendEvent(emitter, "rule", rule);
        ai.whenComplete((answer, error) -> finish(emitter, answer));
    }

    private <T> void finish(SseEmitter emitter, Optional<T> answer) {
        if (answer != null && answer.isPresent()) {
            sendEvent(emitter, "ai", answer.get());
        }
        emitter.complete();
    }

    // 화면이 연결을 먼저 끊으면 보낼 수 없다. 오류로 닫아 연결을 정리한다
    private void sendEvent(SseEmitter emitter, String name, Object data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data, MediaType.APPLICATION_JSON));
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
    }
}
