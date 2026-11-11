package com.bbcc.kidly.boundedcontext.order.out.ai;

import static com.bbcc.kidly.boundedcontext.order.out.ai.AiDiagnosisFixtures.GOOD;
import static com.bbcc.kidly.boundedcontext.order.out.ai.AiDiagnosisFixtures.KEY;
import static com.bbcc.kidly.boundedcontext.order.out.ai.AiDiagnosisFixtures.completion;
import static com.bbcc.kidly.boundedcontext.order.out.ai.AiDiagnosisFixtures.properties;
import static com.bbcc.kidly.boundedcontext.order.out.ai.AiDiagnosisFixtures.request;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.never;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import ch.qos.logback.classic.spi.ILoggingEvent;
import com.bbcc.kidly.boundedcontext.order.CapturedLogs;
import com.bbcc.kidly.boundedcontext.order.app.port.StuckOrderDiagnoser;
import com.bbcc.kidly.boundedcontext.order.app.port.StuckOrderDiagnoser.Diagnosis;
import com.bbcc.kidly.boundedcontext.order.app.port.StuckOrderDiagnoser.Outcome;
import com.bbcc.kidly.boundedcontext.order.out.config.OrderAiProperties;
import java.net.SocketTimeoutException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * OpenAI 호환 Chat Completions 어댑터(#926). 가짜 서버(MockRestServiceServer)만 쓰고 실제로 부르지 않는다.
 */
class OpenAiCompatibleDiagnoserTest {

    private static final String GROQ = "https://api.groq.com/openai/v1";

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

    private OpenAiCompatibleDiagnoser diagnoser(OrderAiProperties properties) {
        return OpenAiCompatibleDiagnoser.create(properties, builder, new AiDiagnosisPrompt(JsonMapper.shared()),
            JsonMapper.shared(), Clock.systemDefaultZone());
    }

    @Test
    @DisplayName("base-url/chat/completions에 Bearer 키 · 모델 · JSON 모드 · 메시지 2개로 보내고, 답을 해석해 제공자(호스트) · 모델과 돌려준다")
    void diagnoses() {
        server.expect(requestTo(GROQ + "/chat/completions"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("Authorization", "Bearer " + KEY))
            .andExpect(jsonPath("$.model").value("openai/gpt-oss-20b"))
            .andExpect(jsonPath("$.response_format.type").value("json_object"))
            .andExpect(jsonPath("$.temperature").value(0))
            .andExpect(jsonPath("$.max_completion_tokens").value(800))
            .andExpect(jsonPath("$.messages[0].role").value("system"))
            .andExpect(jsonPath("$.messages[1].role").value("user"))
            .andRespond(withSuccess(completion(GOOD), MediaType.APPLICATION_JSON));

        Diagnosis diagnosis = diagnoser(properties(GROQ, "openai/gpt-oss-20b", KEY)).diagnose(request());

        server.verify();
        assertThat(diagnosis.outcome()).isEqualTo(Outcome.DIAGNOSED);
        assertThat(diagnosis.confidence()).isEqualTo(StuckOrderDiagnoser.Confidence.MEDIUM);
        assertThat(diagnosis.provider()).isEqualTo("api.groq.com");
        assertThat(diagnosis.model()).isEqualTo("openai/gpt-oss-20b");
    }

    @Test
    @DisplayName("설정만 바꾸면 다른 OpenAI 호환 주소 · 모델로 보낸다(끝 / 는 무시)")
    void switchesByConfiguration() {
        server.expect(requestTo("https://api.openai.com/v1/chat/completions"))
            .andExpect(header("Authorization", "Bearer other-key"))
            .andExpect(jsonPath("$.model").value("gpt-4o-mini"))
            .andRespond(withSuccess(completion(GOOD), MediaType.APPLICATION_JSON));

        Diagnosis diagnosis = diagnoser(properties("https://api.openai.com/v1/", "gpt-4o-mini", "other-key"))
            .diagnose(request());

        server.verify();
        assertThat(diagnosis.provider()).isEqualTo("api.openai.com");
        assertThat(diagnosis.model()).isEqualTo("gpt-4o-mini");
    }

    @Test
    @DisplayName("요청 본문에는 개인정보 칸 · 값이 없다")
    void requestHasNoPersonalData() {
        List<String> bodies = new ArrayList<>();
        server.expect(requestTo(GROQ + "/chat/completions"))
            .andExpect(request -> bodies.add(((MockClientHttpRequest) request).getBodyAsString()))
            .andRespond(withSuccess(completion(GOOD), MediaType.APPLICATION_JSON));

        diagnoser(properties(GROQ, "m", KEY)).diagnose(request());

        assertThat(bodies).singleElement().satisfies(body -> assertThat(body).doesNotContain("recipient", "phone",
            "address", "zipCode", "nickname", "child", "memberId", "actorId", "height", "weight", KEY));
    }

    @Test
    @DisplayName("시간 초과 TIMEOUT · 5xx/4xx HTTP_ERROR · 형식 오류 BAD_RESPONSE는 예외 없이 FAILED로 돌려준다")
    void failuresAreResults() {
        server.expect(requestTo(GROQ + "/chat/completions")).andRespond(withException(new SocketTimeoutException()));
        server.expect(requestTo(GROQ + "/chat/completions")).andRespond(withServerError());
        server.expect(requestTo(GROQ + "/chat/completions")).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        server.expect(requestTo(GROQ + "/chat/completions"))
            .andRespond(withSuccess(completion("결제 복구를 하세요"), MediaType.APPLICATION_JSON));
        server.expect(requestTo(GROQ + "/chat/completions"))
            .andRespond(withSuccess("<html>bad gateway</html>", MediaType.TEXT_HTML));
        // 서킷이 열리지 않게 넉넉한 창
        OrderAiProperties wide = new OrderAiProperties(true, OrderAiProperties.Provider.OPENAI_COMPATIBLE, GROQ, "m",
            KEY, properties(GROQ, "m", KEY).connectTimeout(), properties(GROQ, "m", KEY).timeout(), 800,
            new OrderAiProperties.CircuitBreaker(100, 100, 50, java.time.Duration.ofSeconds(10), 50,
                java.time.Duration.ofMinutes(1), 1));
        OpenAiCompatibleDiagnoser diagnoser = diagnoser(wide);

        List<String> reasons = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Diagnosis diagnosis = diagnoser.diagnose(request());
            assertThat(diagnosis.outcome()).isEqualTo(Outcome.FAILED);
            assertThat(diagnosis.provider()).isEqualTo("api.groq.com");
            reasons.add(diagnosis.reason());
        }

        server.verify();
        assertThat(reasons).containsExactly("TIMEOUT", "HTTP_ERROR", "HTTP_ERROR", "BAD_RESPONSE", "BAD_RESPONSE");
    }

    @Test
    @DisplayName("응답 본문이 64KB를 넘으면 끝까지 읽지 않고 BAD_RESPONSE(64KB 이하면 해석)")
    void rejectsTooLargeResponse() {
        String padding = " ".repeat(OpenAiCompatibleDiagnoser.MAX_RESPONSE_BYTES);
        server.expect(requestTo(GROQ + "/chat/completions"))
            .andRespond(withSuccess(completion(GOOD) + padding, MediaType.APPLICATION_JSON));
        String fits = completion(GOOD);
        server.expect(requestTo(GROQ + "/chat/completions")).andRespond(withSuccess(
            fits + " ".repeat(OpenAiCompatibleDiagnoser.MAX_RESPONSE_BYTES - fits.getBytes(
                java.nio.charset.StandardCharsets.UTF_8).length), MediaType.APPLICATION_JSON));
        OpenAiCompatibleDiagnoser diagnoser = diagnoser(properties(GROQ, "m", KEY));

        Diagnosis tooLarge = diagnoser.diagnose(request());
        Diagnosis atLimit = diagnoser.diagnose(request());

        server.verify();
        assertThat(tooLarge.outcome()).isEqualTo(Outcome.FAILED);
        assertThat(tooLarge.reason()).isEqualTo("BAD_RESPONSE");
        assertThat(atLimit.outcome()).isEqualTo(Outcome.DIAGNOSED);
    }

    @Test
    @DisplayName("실패가 기준을 넘으면 서킷이 열려 부르지 않고 NOT_CALLED(CIRCUIT_OPEN)를 돌려준다")
    void circuitOpens() {
        // 창 4 · 최소 2 · 실패율 50%: 실패 2번이면 열린다
        server.expect(requestTo(GROQ + "/chat/completions")).andRespond(withServerError());
        server.expect(requestTo(GROQ + "/chat/completions")).andRespond(withServerError());
        server.expect(never(), requestTo(GROQ + "/chat/completions"));
        OpenAiCompatibleDiagnoser diagnoser = diagnoser(properties(GROQ, "m", KEY));

        diagnoser.diagnose(request());
        diagnoser.diagnose(request());
        Diagnosis third = diagnoser.diagnose(request());

        server.verify();
        assertThat(third).isEqualTo(Diagnosis.notCalled("CIRCUIT_OPEN"));
    }

    @Test
    @DisplayName("로그에는 키 · 프롬프트 · 응답 본문이 없다(성공 · 실패 · 서킷 열림 모두)")
    void logsHaveNoSecretsOrBodies() {
        String leak = "{\"summary\":\"홍길동 010-1234-5678\",\"recommendedAction\":\"x\",\"confidence\":\"SURE\"}";
        server.expect(requestTo(GROQ + "/chat/completions"))
            .andRespond(withSuccess(completion(GOOD), MediaType.APPLICATION_JSON));
        server.expect(requestTo(GROQ + "/chat/completions"))
            .andRespond(withSuccess(completion(leak), MediaType.APPLICATION_JSON));
        server.expect(requestTo(GROQ + "/chat/completions"))
            .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("{\"error\":\"invalid key " + KEY + "\"}"));
        OpenAiCompatibleDiagnoser diagnoser = diagnoser(properties(GROQ, "m", KEY));

        List<ILoggingEvent> events;
        try (CapturedLogs logs = CapturedLogs.start()) {
            diagnoser.diagnose(request());
            diagnoser.diagnose(request());
            diagnoser.diagnose(request());
            diagnoser.diagnose(request());
            events = logs.events();
        }

        assertThat(events).isNotEmpty().allSatisfy(event -> {
            String message = event.getFormattedMessage();
            assertThat(message).doesNotContain(KEY, "홍길동", "010-1234-5678", "결제는 끝났지만", "멈춘 주문 정보",
                "PAID_WITHOUT_DELIVERY", "delivery-failed");
            assertThat(event.getThrowableProxy()).isNull();
        });
    }
}
