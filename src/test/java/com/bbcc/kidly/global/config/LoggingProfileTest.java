package com.bbcc.kidly.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.OutputStreamAppender;
import ch.qos.logback.core.encoder.Encoder;
import com.bbcc.kidly.global.filter.RequestLoggingFilter;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.Banner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.logging.logback.StructuredLogEncoder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * logback-spring.xml이 프로필마다 어디로 로그를 남기는지 확인한다.
 * 빈은 띄우지 않고 스프링 로깅 초기화만 거친 뒤 root logger의 출력 대상(appender)과 콘솔 출력 형식을 본다.
 */
@ExtendWith(OutputCaptureExtension.class)
class LoggingProfileTest {

    @TempDir
    private Path logPath;

    @AfterEach
    void restoreLogging() {
        startLogging("test");
    }

    @Test
    @DisplayName("로컬 프로필은 콘솔에만 남긴다")
    void localLogsToConsole() {
        startLogging("local");

        assertThat(rootAppenders()).containsExactly("CONSOLE");
        assertThat(consoleEncoder()).isInstanceOf(PatternLayoutEncoder.class);
    }

    @Test
    @DisplayName("운영 프로필은 콘솔과 파일(일반 · 에러, 비동기)에 남긴다")
    void prodLogsToConsoleAndFiles() {
        startLogging("prod");

        assertThat(rootAppenders()).containsExactlyInAnyOrder("CONSOLE", "ASYNC_FILE", "ASYNC_ERROR_FILE");
    }

    @Test
    @DisplayName("운영 프로필 콘솔은 요청 로그를 ECS JSON 한 줄로 남기고 traceId · 요청 필드를 넣는다")
    void prodConsoleWritesRequestLogAsEcsJson(CapturedOutput output) throws Exception {
        startLogging("prod");
        var response = new MockHttpServletResponse();

        new RequestLoggingFilter().doFilter(
            new MockHttpServletRequest("GET", "/api/products"), response, new MockFilterChain());

        assertThat(consoleEncoder()).isInstanceOf(StructuredLogEncoder.class);
        var json = lineContaining(output, "GET /api/products");
        assertThat(JsonPath.<String>read(json, "$.log.level")).isEqualTo("INFO");
        assertThat(JsonPath.<String>read(json, "$.message")).startsWith("GET /api/products - 200");
        assertThat(JsonPath.<String>read(json, "$.service.name")).isEqualTo("kidly");
        assertThat(JsonPath.<String>read(json, "$.traceId")).isEqualTo(response.getHeader("X-Request-Id"));
        assertThat(JsonPath.<String>read(json, "$.http.request.method")).isEqualTo("GET");
        assertThat(JsonPath.<String>read(json, "$.url.path")).isEqualTo("/api/products");
        assertThat(JsonPath.<Integer>read(json, "$.http.response.status_code")).isEqualTo(200);
        assertThat(JsonPath.<Number>read(json, "$.duration_ms")).isNotNull();
    }

    @Test
    @DisplayName("운영 프로필 콘솔은 요청 상세(헤더 · 본문 · IP · 회원 ID)를 가린 채 JSON 객체로 남긴다")
    void prodConsoleWritesRequestDetails(CapturedOutput output) throws Exception {
        startLogging("prod");
        var request = new MockHttpServletRequest("POST", "/api/v1/orders");
        request.setQueryString("page=1&token=abc");
        request.addHeader("Authorization", "Bearer secret-token");
        request.addHeader("User-Agent", "Mozilla/5.0");
        request.setContentType("application/json");
        request.setContent("{\"recipientPhone\":\"010-1234-5678\",\"amount\":1000}".getBytes(StandardCharsets.UTF_8));

        new RequestLoggingFilter().doFilter(request, new MockHttpServletResponse(), (req, res) -> {
            req.getInputStream().readAllBytes();
            MDC.put(RequestLoggingFilter.MEMBER_ID_MDC_KEY, "7");
            res.setContentType("application/json");
            res.getWriter().write("{\"orderId\":\"o-1\"}");
        });

        var json = lineContaining(output, "POST /api/v1/orders");
        assertThat(json).doesNotContain("secret-token", "010-1234-5678", "token=abc");
        assertThat(JsonPath.<String>read(json, "$.memberId")).isEqualTo("7");
        assertThat(JsonPath.<String>read(json, "$.client.ip")).isEqualTo("127.0.0.1");
        assertThat(JsonPath.<String>read(json, "$.url.query")).isEqualTo("page=1&token=***");
        assertThat(JsonPath.<String>read(json, "$.user_agent.original")).isEqualTo("Mozilla/5.0");
        assertThat(JsonPath.<String>read(json, "$.http.request.method")).isEqualTo("POST");
        assertThat(JsonPath.<String>read(json, "$.http.request.headers.authorization")).isEqualTo("***");
        assertThat(JsonPath.<String>read(json, "$.http.request.body.content"))
            .isEqualTo("{\"recipientPhone\":\"***\",\"amount\":1000}");
        assertThat(JsonPath.<String>read(json, "$.http.response.body.content")).isEqualTo("{\"orderId\":\"o-1\"}");
    }

    @Test
    @DisplayName("운영 프로필 콘솔은 예외를 같은 JSON 줄의 error 필드로 남긴다")
    void prodConsoleWritesExceptionInSameLine(CapturedOutput output) {
        startLogging("prod");

        LoggerFactory.getLogger(LoggingProfileTest.class).error("결제 승인 실패", new IllegalStateException("boom"));

        var json = lineContaining(output, "결제 승인 실패");
        assertThat(JsonPath.<String>read(json, "$.log.level")).isEqualTo("ERROR");
        assertThat(JsonPath.<String>read(json, "$.error.type")).isEqualTo(IllegalStateException.class.getName());
        assertThat(JsonPath.<String>read(json, "$.error.message")).isEqualTo("boom");
        assertThat(JsonPath.<String>read(json, "$.error.stack_trace")).contains("prodConsoleWritesExceptionInSameLine");
    }

    @Test
    @DisplayName("운영 프로필 콘솔은 예외마다 스택 프레임을 30개까지만 남기고 Caused by는 남긴다")
    void prodConsoleLimitsStackFrames(CapturedOutput output) {
        startLogging("prod");

        LoggerFactory.getLogger(LoggingProfileTest.class)
            .error("깊은 스택", new IllegalStateException("바깥", deepException(200)));

        var stackTrace = JsonPath.<String>read(lineContaining(output, "깊은 스택"), "$.error.stack_trace");
        assertThat(stackTrace).contains("Caused by: java.lang.IllegalArgumentException: 안쪽");
        assertThat(stackTrace.lines().filter(line -> line.startsWith("\tat "))).hasSizeLessThanOrEqualTo(60);
    }

    @Test
    @DisplayName("테스트 프로필은 콘솔에만 남긴다")
    void testLogsToConsole() {
        startLogging("test");

        assertThat(rootAppenders()).containsExactly("CONSOLE");
        assertThat(consoleEncoder()).isInstanceOf(PatternLayoutEncoder.class);
    }

    private void startLogging(String profile) {
        try (var context = new SpringApplicationBuilder(EmptyConfig.class)
            .web(WebApplicationType.NONE)
            .bannerMode(Banner.Mode.OFF)
            .logStartupInfo(false)
            .profiles(profile)
            .run("--logging.file.path=" + logPath)) {
            assertThat(context.isActive()).isTrue();
        }
    }

    private static List<String> rootAppenders() {
        List<String> names = new ArrayList<>();
        for (Iterator<Appender<ILoggingEvent>> it = rootLogger().iteratorForAppenders(); it.hasNext(); ) {
            names.add(it.next().getName());
        }
        return names;
    }

    private static Encoder<ILoggingEvent> consoleEncoder() {
        return ((OutputStreamAppender<ILoggingEvent>) rootLogger().getAppender("CONSOLE")).getEncoder();
    }

    private static Logger rootLogger() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        return context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
    }

    private static RuntimeException deepException(int depth) {
        return depth == 0 ? new IllegalArgumentException("안쪽") : deepException(depth - 1);
    }

    private static String lineContaining(CapturedOutput output, String text) {
        return output.getOut().lines()
            .filter(line -> line.contains(text))
            .reduce((first, second) -> second)
            .orElseThrow();
    }

    @Configuration(proxyBeanMethods = false)
    static class EmptyConfig {
    }
}
