package com.bbcc.kidly.global.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;

class RequestLoggingFilterTest {

    private final RequestLoggingFilter filter = new RequestLoggingFilter();
    private final Logger logger = (Logger) LoggerFactory.getLogger(RequestLoggingFilter.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>() {
        @Override
        protected void append(ILoggingEvent event) {
            event.prepareForDeferredProcessing();
            super.append(event);
        }
    };

    @BeforeEach
    void attach() {
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detach() {
        logger.detachAppender(appender);
        MDC.clear();
    }

    @Test
    void recordsOnlyNormalizedRequestMetadataAndTrace() throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/orders/synthetic-id");
        request.setQueryString("email=synthetic@example.invalid&token=synthetic-token");
        request.setRemoteAddr("203.0.113.7");
        request.addHeader("Authorization", "Bearer synthetic-token");
        request.addHeader("X-Member-Id", "123");
        request.addHeader("X-Admin-Id", "456");
        request.addHeader("X-Child-Account-Id", "789");
        request.addHeader("User-Agent", "synthetic-agent");
        request.setContent("synthetic-request-body".getBytes(StandardCharsets.UTF_8));
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> {
            assertThat(req.getInputStream().readAllBytes()).isEqualTo(request.getContentAsByteArray());
            req.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/orders/{id}");
            res.getWriter().write("synthetic-response-body");
        });
        var event = appender.list.getFirst();
        assertThat(fields(event)).containsOnlyKeys("http.request.method", "url.path", "http.route",
            "http.response.status_code", "duration_ms");
        assertThat(fields(event)).containsEntry("http.route", "/api/orders/{id}")
            .containsEntry("url.path", "/api/orders/{id}").containsEntry("http.response.status_code", 200);
        assertThat(event.getFormattedMessage()).startsWith("POST /api/orders/{id} - 200");
        assertThat(event.getMDCPropertyMap()).containsOnlyKeys("traceId");
        assertThat(response.getContentAsString()).isEqualTo("synthetic-response-body");
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @Test
    void unknownRouteNeverFallsBackToRawUrl() throws Exception {
        var request = new MockHttpServletRequest("GET", "/unknown/synthetic-private-value");
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> { });
        assertThat(fields(appender.list.getFirst())).containsEntry("http.route", "UNKNOWN");
        assertThat(appender.list.getFirst().getFormattedMessage()).doesNotContain("synthetic-private-value");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/auth/login", "/api/v1/webhooks/toss", "/api/v1/products"})
    void neverWrapsOrBuffersBodies(String path) throws Exception {
        var request = new MockHttpServletRequest("POST", path);
        var response = new MockHttpServletResponse();
        String large = "x".repeat(100_000);
        filter.doFilter(request, response, (req, res) -> {
            assertThat(req).isSameAs(request);
            assertThat(res).isSameAs(response);
            res.getWriter().write(large);
        });
        assertThat(response.getContentAsString()).isEqualTo(large);
        assertThat(fields(appender.list.getFirst())).doesNotContainKeys("http.request.body.content",
            "http.response.body.content");
    }

    @Test
    void retainsValidRequestId() throws Exception {
        String trace = UUID.randomUUID().toString();
        var request = new MockHttpServletRequest("GET", "/api/test");
        request.addHeader("X-Request-Id", trace);
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> assertThat(MDC.get("traceId")).isEqualTo(trace));
        assertThat(response.getHeader("X-Request-Id")).isEqualTo(trace);
        assertThat(appender.list.getFirst().getMDCPropertyMap()).containsEntry("traceId", trace);
    }

    @Test
    void replacesInvalidRequestId() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/test");
        request.addHeader("X-Request-Id", "synthetic-untrusted-value");
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> { });
        assertThat(UUID.fromString(response.getHeader("X-Request-Id"))).isNotNull();
    }

    @Test
    void skipsHealthyActuatorButRecordsFailure() throws Exception {
        var healthy = new MockHttpServletRequest("GET", "/actuator/health");
        filter.doFilter(healthy, new MockHttpServletResponse(), (req, res) -> { });
        assertThat(appender.list).isEmpty();
        var failed = new MockHttpServletRequest("GET", "/actuator/health");
        filter.doFilter(failed, new MockHttpServletResponse(),
            (req, res) -> ((MockHttpServletResponse) res).setStatus(503));
        assertThat(appender.list).hasSize(1);
        assertThat(fields(appender.list.getFirst())).containsEntry("http.response.status_code", 503)
            .containsEntry("http.route", "/actuator");
    }

    @Test
    void clearsTraceOnFailureWithoutChangingException() {
        var request = new MockHttpServletRequest("GET", "/api/test");
        assertThatThrownBy(() -> filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
            throw new IOException("synthetic failure");
        })).isInstanceOf(IOException.class).hasMessage("synthetic failure");
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    private static Map<String, Object> fields(ILoggingEvent event) {
        var result = new LinkedHashMap<String, Object>();
        event.getKeyValuePairs().forEach(pair -> result.put(pair.key, pair.value));
        return result;
    }
}
