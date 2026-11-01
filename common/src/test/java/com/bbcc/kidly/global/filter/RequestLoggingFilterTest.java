package com.bbcc.kidly.global.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.bbcc.kidly.shared.auth.MemberHeaders;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartHttpServletRequest;
import org.springframework.web.servlet.HandlerMapping;

class RequestLoggingFilterTest {

    private final RequestLoggingFilter filter = new RequestLoggingFilter();
    private final Logger logger = (Logger) LoggerFactory.getLogger(RequestLoggingFilter.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>() {
        @Override
        protected void append(ILoggingEvent event) {
            // MDC는 필터가 끝나며 지워지므로 로그를 남긴 시점의 값을 붙잡아 둔다
            event.prepareForDeferredProcessing();
            super.append(event);
        }
    };

    @BeforeEach
    void attachAppender() {
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
    }

    @Test
    @DisplayName("일반 요청은 끝날 때 로그 한 줄을 남기고, 요청 필드와 traceId를 붙인다")
    void logsRequestWithFields() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/products");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, respondWith(201));

        assertThat(appender.list).hasSize(1);
        var end = appender.list.getFirst();
        assertThat(end.getFormattedMessage()).matches("GET /api/products - 201 \\(\\d+ms\\)");
        assertThat(end.getKeyValuePairs())
            .extracting(pair -> pair.key, pair -> pair.value == null ? null : pair.value.getClass())
            .startsWith(
                tuple("http.request.method", String.class),
                tuple("url.path", String.class),
                tuple("http.response.status_code", Integer.class),
                tuple("duration_ms", Long.class));
        assertThat(end.getKeyValuePairs()).extracting(pair -> pair.value)
            .startsWith("GET", "/api/products", 201);
        var traceId = response.getHeader("X-Request-Id");
        assertThat(traceId).isNotBlank();
        assertThat(end.getMDCPropertyMap()).containsEntry(RequestLoggingFilter.TRACE_ID_MDC_KEY, traceId);
        assertThat(MDC.get(RequestLoggingFilter.TRACE_ID_MDC_KEY)).isNull();
    }

    @Test
    @DisplayName("actuator 요청이 2xx면 로그를 남기지 않는다. X-Request-Id는 붙인다")
    void skipsSuccessfulActuatorRequest() throws Exception {
        var request = new MockHttpServletRequest("GET", "/actuator/health");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, respondWith(200));

        assertThat(appender.list).isEmpty();
        assertThat(response.getHeader("X-Request-Id")).isNotBlank();
    }

    @Test
    @DisplayName("actuator 요청은 본문을 남기지 않으므로 요청 · 응답을 감싸지 않는다")
    void doesNotWrapActuatorRequest() throws Exception {
        var request = new MockHttpServletRequest("GET", "/actuator/prometheus");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
            assertThat(req).isSameAs(request);
            assertThat(res).isSameAs(response);
            res.getWriter().write("jvm_threads_live 1");
        });

        assertThat(response.getContentAsString()).isEqualTo("jvm_threads_live 1");
        assertThat(response.getHeader("X-Request-Id")).isNotBlank();
    }

    @Test
    @DisplayName("actuator 요청이 2xx가 아니면 로그 한 줄만 남긴다")
    void logsFailedActuatorRequestOnce() throws Exception {
        var request = new MockHttpServletRequest("GET", "/actuator/health");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, respondWith(503));

        assertThat(appender.list).singleElement()
            .extracting(ILoggingEvent::getFormattedMessage).asString()
            .startsWith("GET /actuator/health - 503");
    }

    @Test
    @DisplayName("경로가 /actuator로 시작하기만 하는 요청은 일반 요청으로 남긴다")
    void logsActuatorLookalikeAsNormalRequest() throws Exception {
        var request = new MockHttpServletRequest("GET", "/actuatorx");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, respondWith(200));

        assertThat(appender.list).singleElement()
            .extracting(ILoggingEvent::getFormattedMessage).asString()
            .startsWith("GET /actuatorx - 200");
    }

    @Test
    @DisplayName("IP · 경로 패턴 · User-Agent를 남기고 헤더는 소문자 이름으로 비밀값을 가린다. User-Agent는 헤더에 다시 넣지 않는다")
    void logsClientAndMaskedHeaders() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/v1/products/3");
        request.setRemoteAddr("203.0.113.7");
        request.addHeader("User-Agent", "Mozilla/5.0");
        request.addHeader("Authorization", "Bearer secret-token");
        request.addHeader("Cookie", "refresh_token=abc");
        request.addHeader("Proxy-Authorization", "Basic abc");
        request.addHeader("Accept", "application/json");
        request.addHeader("X-Api-Key", "k");
        request.addHeader("X-Auth-Token", "t");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
            req.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/v1/products/{id}");
            ((HttpServletResponse) res).addHeader("Set-Cookie", "refresh_token=new");
            ((HttpServletResponse) res).addHeader("Cache-Control", "no-store");
        });

        var fields = keyValues(appender.list.getFirst());
        assertThat(fields).containsEntry("client.ip", "203.0.113.7")
            .containsEntry("http.route", "/api/v1/products/{id}")
            .containsEntry("user_agent.original", "Mozilla/5.0");
        assertThat(fields.get("http.request.headers")).asInstanceOf(InstanceOfAssertFactories.MAP)
            .containsEntry("authorization", "***")
            .containsEntry("cookie", "***")
            .containsEntry("proxy-authorization", "***")
            .containsEntry("accept", "application/json")
            .containsEntry("x-api-key", "***")
            .containsEntry("x-auth-token", "***")
            .doesNotContainKey("user-agent");
        assertThat(fields.get("http.response.headers")).asInstanceOf(InstanceOfAssertFactories.MAP)
            .containsEntry("set-cookie", "***")
            .containsEntry("cache-control", "no-store");
        assertThat(appender.list.getFirst().getFormattedMessage()).doesNotContain("secret-token");
    }

    @Test
    @DisplayName("헤더 값은 300자까지 남기고 잘렸다고 표시한다")
    void truncatesLongHeader() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/v1/products");
        request.addHeader("Referer", "a".repeat(400));
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, respondWith(200));

        assertThat(keyValues(appender.list.getFirst()).get("http.request.headers"))
            .asInstanceOf(InstanceOfAssertFactories.MAP)
            .containsEntry("referer", "a".repeat(300) + "…(잘림, 전체 400자)");
    }

    @Test
    @DisplayName("쿼리 문자열은 비밀값 · 연락처 · 주소 계열 파라미터 값을 가린다")
    void masksQuery() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/v1/products");
        request.setQueryString("keyword=%EC%85%94%EC%B8%A0&token=abc&page=1&email=a%40b.c&flag");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, respondWith(200));

        assertThat(keyValues(appender.list.getFirst()))
            .containsEntry("url.query", "keyword=%EC%85%94%EC%B8%A0&token=***&page=1&email=***&flag");
    }

    @Test
    @DisplayName("이름을 디코드하지 못하는 쿼리 파라미터는 가리고, 응답 본문은 그대로 보내고 MDC를 비운다")
    void masksUndecodableQueryParam() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/v1/products");
        request.setQueryString("a%=1&%zz=2&page=3");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> res.getWriter().write("ok"));

        assertThat(keyValues(appender.list.getFirst())).containsEntry("url.query", "a%=***&%zz=***&page=3");
        assertThat(response.getContentAsString()).isEqualTo("ok");
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @Test
    @DisplayName("로그를 남기다 실패해도 응답 본문은 그대로 보내고 MDC를 비운다")
    void cleansUpWhenLoggingFails() {
        var request = new MockHttpServletRequest("GET", "/api/v1/products") {
            @Override
            public String getRemoteAddr() {
                throw new IllegalStateException("로그 실패");
            }
        };
        var response = new MockHttpServletResponse();

        assertThatThrownBy(() -> filter.doFilter(request, response, (req, res) -> {
            MDC.put(RequestLoggingFilter.MEMBER_ID_MDC_KEY, "7");
            res.getWriter().write("ok");
        })).hasMessage("로그 실패");

        assertThat(response.getContentAsByteArray()).asString(StandardCharsets.UTF_8).isEqualTo("ok");
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @Test
    @DisplayName("JSON 본문은 비밀값 · 연락처 · 주소 계열 키를 중첩 · 배열 안까지 가리고(code 같은 일반 값은 남김), 응답은 원래 본문 그대로 보낸다")
    void masksJsonBodies() throws Exception {
        var request = jsonRequest("POST", "/api/v1/orders", """
            {"password":"pw","name":"홍길동","amount":1000,
             "shippingAddress":{"roadAddress":"서울시","zipCode":"01234"},
             "items":[{"recipientPhone":"010-1234-5678","accessToken":"t","code":"c","skuCode":"S-1"}],
             "businessRegistrationNumber":"123-45-67890","uploadUrl":"https://s3/presigned?X-Amz-Signature=x"}""");
        var response = new MockHttpServletResponse();
        var responseBody = "{\"id\":1,\"email\":\"a@b.c\",\"refreshToken\":\"r\"}";

        filter.doFilter(request, response, (req, res) -> {
            req.getInputStream().readAllBytes();
            res.setContentType("application/json");
            res.getWriter().write(responseBody);
        });

        var fields = keyValues(appender.list.getFirst());
        assertThat(fields.get("http.request.body.content")).isEqualTo(
            "{\"password\":\"***\",\"name\":\"홍길동\",\"amount\":1000,\"shippingAddress\":\"***\","
                + "\"items\":[{\"recipientPhone\":\"***\",\"accessToken\":\"***\","
                + "\"code\":\"c\",\"skuCode\":\"S-1\"}],"
                + "\"businessRegistrationNumber\":\"***\",\"uploadUrl\":\"***\"}");
        assertThat(fields.get("http.response.body.content"))
            .isEqualTo("{\"id\":1,\"email\":\"***\",\"refreshToken\":\"***\"}");
        assertThat(response.getContentAsString()).isEqualTo(responseBody);
    }

    @Test
    @DisplayName("본문은 UTF-8 3,000바이트까지 글자 경계에서 자르고 잘렸다고 표시한다")
    void truncatesLongBody() throws Exception {
        var request = jsonRequest("POST", "/api/v1/notes", "{\"memo\":\"a" + "가".repeat(1500) + "\"}");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> req.getInputStream().readAllBytes());

        assertThat(keyValues(appender.list.getFirst()).get("http.request.body.content")).asString()
            .isEqualTo("{\"memo\":\"a" + "가".repeat(996) + "…(잘림, 전체 4512바이트)");
    }

    @Test
    @DisplayName("텍스트 본문은 가릴 방법이 없어 남기지 않는다")
    void skipsTextBody() throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/v1/notes");
        request.setContentType("text/plain;charset=UTF-8");
        request.setContent("password=pw".getBytes(StandardCharsets.UTF_8));
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
            req.getInputStream().readAllBytes();
            res.setContentType("text/plain");
            res.getWriter().write("ok");
        });

        assertThat(keyValues(appender.list.getFirst()))
            .doesNotContainKeys("http.request.body.content", "http.response.body.content");
        assertThat(response.getContentAsString()).isEqualTo("ok");
    }

    @Test
    @DisplayName("자녀 프로필의 생년월일 · 키 · 몸무게는 가리고 이름 · 상품 치수 범위는 남긴다")
    void masksChildProfileBody() throws Exception {
        var request = jsonRequest("POST", "/api/v1/members/me/children", """
            {"name":"하늘","birthDate":"2020-05-01","heightCm":110.5,"weightKg":18.2,"minHeightCm":100}""");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> req.getInputStream().readAllBytes());

        assertThat(keyValues(appender.list.getFirst())).containsEntry("http.request.body.content",
            "{\"name\":\"하늘\",\"birthDate\":\"***\",\"heightCm\":\"***\",\"weightKg\":\"***\",\"minHeightCm\":100}");
    }

    @Test
    @DisplayName("64KB가 넘는 응답 본문은 읽지 않고 크기만 남긴다")
    void skipsHugeResponseBody() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/v1/products");
        var response = new MockHttpServletResponse();
        var body = "[" + "1,".repeat(40_000) + "1]";

        filter.doFilter(request, response, (req, res) -> {
            res.setContentType("application/json");
            res.getWriter().write(body);
        });

        assertThat(keyValues(appender.list.getFirst()))
            .containsEntry("http.response.body.content", "(" + body.length() + "바이트, 생략)");
        assertThat(response.getContentAsString()).isEqualTo(body);
    }

    @Test
    @DisplayName("JSON으로 읽지 못한 본문은 가릴 수 없으므로 내용 대신 표시만 남긴다")
    void hidesUnreadableJson() throws Exception {
        var request = jsonRequest("POST", "/api/v1/orders", "{\"password\":\"pw\"");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> req.getInputStream().readAllBytes());

        assertThat(keyValues(appender.list.getFirst()).get("http.request.body.content")).asString()
            .doesNotContain("pw");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/auth/reissue", "/api/v1/auth/login/kakao", "/api/v1/dev/tokens",
        "/api/v1/webhooks/toss"})
    @DisplayName("인증 · 개발용 토큰 · 결제 웹훅 경로는 본문을 남기지 않고 헤더는 가려서 남긴다")
    void skipsBodyOnSensitivePaths(String path) throws Exception {
        var request = jsonRequest("POST", path, "{\"code\":\"c\"}");
        request.addHeader("Authorization", "Bearer t");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
            req.getInputStream().readAllBytes();
            res.setContentType("application/json");
            res.getWriter().write("{\"accessToken\":\"t\"}");
        });

        var fields = keyValues(appender.list.getFirst());
        assertThat(fields).doesNotContainKeys("http.request.body.content", "http.response.body.content");
        assertThat(fields.get("http.request.headers")).asInstanceOf(InstanceOfAssertFactories.MAP)
            .containsEntry("authorization", "***");
        assertThat(response.getContentAsString()).isEqualTo("{\"accessToken\":\"t\"}");
    }

    @Test
    @DisplayName("multipart 요청 · 바이너리 응답은 본문을 남기지 않는다")
    void skipsMultipartAndBinaryBodies() throws Exception {
        var request = new MockMultipartHttpServletRequest();
        request.setRequestURI("/api/v1/images");
        request.setContentType("multipart/form-data; boundary=x");
        request.setContent("--x\r\nbinary\r\n--x--".getBytes(StandardCharsets.UTF_8));
        var response = new MockHttpServletResponse();
        var png = new byte[] {(byte) 0x89, 'P', 'N', 'G'};

        filter.doFilter(request, response, (req, res) -> {
            req.getInputStream().readAllBytes();
            res.setContentType("image/png");
            res.getOutputStream().write(png);
        });

        assertThat(keyValues(appender.list.getFirst()))
            .doesNotContainKeys("http.request.body.content", "http.response.body.content");
        assertThat(response.getContentAsByteArray()).isEqualTo(png);
    }

    @Test
    @DisplayName("X-Member-Id 헤더의 회원 ID를 요청 동안 MDC에 넣어 끝 로그에 남기고 요청이 끝나면 지운다")
    void logsAndClearsMemberId() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/v1/me");
        request.addHeader(MemberHeaders.MEMBER_ID, "7");
        var response = new MockHttpServletResponse();
        var duringRequest = new AtomicReference<String>();

        filter.doFilter(request, response,
            (req, res) -> duringRequest.set(MDC.get(RequestLoggingFilter.MEMBER_ID_MDC_KEY)));

        assertThat(duringRequest).hasValue("7");
        assertThat(appender.list.getFirst().getMDCPropertyMap())
            .containsEntry(RequestLoggingFilter.MEMBER_ID_MDC_KEY, "7");
        assertThat(MDC.get(RequestLoggingFilter.MEMBER_ID_MDC_KEY)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "7 OR 1=1", "", "-7", "9999999999999999999", "12345678901234567890"})
    @DisplayName("X-Member-Id 헤더가 Long 범위의 숫자가 아니면 MDC에 넣지 않는다")
    void skipsInvalidMemberId(String memberId) throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/v1/me");
        request.addHeader(MemberHeaders.MEMBER_ID, memberId);

        filter.doFilter(request, new MockHttpServletResponse(), respondWith(200));

        assertThat(appender.list.getFirst().getMDCPropertyMap())
            .doesNotContainKey(RequestLoggingFilter.MEMBER_ID_MDC_KEY);
    }

    private static MockHttpServletRequest jsonRequest(String method, String uri, String body) {
        var request = new MockHttpServletRequest(method, uri);
        request.setContentType("application/json");
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        return request;
    }

    private static Map<String, Object> keyValues(ILoggingEvent event) {
        var fields = new LinkedHashMap<String, Object>();
        event.getKeyValuePairs().forEach(pair -> fields.put(pair.key, pair.value));
        return fields;
    }

    private static FilterChain respondWith(int status) {
        return (request, response) -> ((HttpServletResponse) response).setStatus(status);
    }
}
