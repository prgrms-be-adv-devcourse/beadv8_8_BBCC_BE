package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.in.support.AiResultStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 규칙 결과와 나중에 끝나는 AI 결과를 SSE 이벤트(rule · ai)로 보내는 순서를 확인한다.
 */
class AiResultStreamTest {

    private final AiResultStream stream = new AiResultStream();

    @Test
    @DisplayName("AI 결과가 이미 끝나 있으면(저장한 답) ai 이벤트만 보내고 닫는다")
    void sendOnlyAiWhenAlreadyDone() {
        RecordingEmitter emitter = new RecordingEmitter();

        stream.send(emitter, "규칙", CompletableFuture.completedFuture(Optional.of("AI")));

        assertThat(emitter.events).containsExactly("ai=AI");
        assertThat(emitter.completed).isTrue();
    }

    @Test
    @DisplayName("AI 결과가 아직이면 rule 이벤트를 먼저 보내고, AI 결과가 오면 ai 이벤트를 보낸 뒤 닫는다")
    void sendRuleThenAi() {
        RecordingEmitter emitter = new RecordingEmitter();
        CompletableFuture<Optional<String>> ai = new CompletableFuture<>();

        stream.send(emitter, "규칙", ai);

        assertThat(emitter.events).containsExactly("rule=규칙");
        assertThat(emitter.completed).isFalse();
        ai.complete(Optional.of("AI"));
        assertThat(emitter.events).containsExactly("rule=규칙", "ai=AI");
        assertThat(emitter.completed).isTrue();
    }

    @Test
    @DisplayName("AI 결과를 얻지 못하면 rule 이벤트만 보낸 채 닫는다")
    void sendOnlyRuleWhenAiIsEmpty() {
        RecordingEmitter emitter = new RecordingEmitter();
        CompletableFuture<Optional<String>> ai = new CompletableFuture<>();

        stream.send(emitter, "규칙", ai);
        ai.complete(Optional.empty());

        assertThat(emitter.events).containsExactly("rule=규칙");
        assertThat(emitter.completed).isTrue();
    }

    @Test
    @DisplayName("제한 시간으로 연결이 먼저 닫혀 ai 이벤트를 보내지 못해도 예외 없이 닫기를 마친다")
    void completeEvenWhenSendFailsAfterClosed() {
        RecordingEmitter emitter = new RecordingEmitter();
        CompletableFuture<Optional<String>> ai = new CompletableFuture<>();
        stream.send(emitter, "규칙", ai);

        emitter.closed = true;
        ai.complete(Optional.of("AI"));

        assertThat(emitter.events).containsExactly("rule=규칙");
        assertThat(emitter.completed).isTrue();
    }

    /**
     * 보낸 이벤트를 「이름=본문」으로 남기는 가짜 SseEmitter다.
     */
    private static final class RecordingEmitter extends SseEmitter {

        private final List<String> events = new ArrayList<>();

        private boolean completed;

        // true면 이미 닫힌 연결처럼 보내기에서 IllegalStateException을 던진다
        private boolean closed;

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            if (closed) {
                throw new IllegalStateException("이미 닫힌 연결입니다");
            }
            String name = null;
            Object data = null;
            // 이벤트 이름 줄(event:)은 text/plain 조각에 들어 있고, 본문은 미디어 타입이 없는 조각이다
            for (DataWithMediaType part : builder.build()) {
                if (part.getMediaType() != null && part.getMediaType().isCompatibleWith(MediaType.TEXT_PLAIN)) {
                    String text = String.valueOf(part.getData());
                    int start = text.indexOf("event:");
                    if (start >= 0) {
                        name = text.substring(start + "event:".length(), text.indexOf('\n', start));
                    }
                } else {
                    data = part.getData();
                }
            }
            events.add(name + "=" + data);
        }

        @Override
        public void complete() {
            completed = true;
        }
    }
}
