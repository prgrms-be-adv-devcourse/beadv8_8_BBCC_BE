package com.bbcc.kidly.global.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

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
    @DisplayName("일반 요청은 시작 · 끝 로그를 남기고, 끝 로그에 요청 필드와 traceId를 붙인다")
    void logsRequestWithFields() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/products");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, respondWith(201));

        assertThat(appender.list).hasSize(2);
        assertThat(appender.list.get(0).getFormattedMessage()).isEqualTo(">>> GET /api/products");
        var end = appender.list.get(1);
        assertThat(end.getFormattedMessage()).matches("<<< GET /api/products - 201 \\(\\d+ms\\)");
        assertThat(end.getKeyValuePairs())
            .extracting(pair -> pair.key, pair -> pair.value == null ? null : pair.value.getClass())
            .containsExactly(
                tuple("http.request.method", String.class),
                tuple("url.path", String.class),
                tuple("http.response.status_code", Integer.class),
                tuple("duration_ms", Long.class));
        assertThat(end.getKeyValuePairs()).extracting(pair -> pair.value)
            .startsWith("GET", "/api/products", 201);
        var traceId = response.getHeader("X-Request-Id");
        assertThat(traceId).isNotBlank();
        assertThat(appender.list).allSatisfy(event ->
            assertThat(event.getMDCPropertyMap()).containsEntry(RequestLoggingFilter.TRACE_ID_MDC_KEY, traceId));
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
    @DisplayName("actuator 요청이 2xx가 아니면 끝 로그 한 줄만 남긴다")
    void logsFailedActuatorRequestOnce() throws Exception {
        var request = new MockHttpServletRequest("GET", "/actuator/health");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, respondWith(503));

        assertThat(appender.list).singleElement()
            .extracting(ILoggingEvent::getFormattedMessage).asString()
            .startsWith("<<< GET /actuator/health - 503");
    }

    @Test
    @DisplayName("경로가 /actuator로 시작하기만 하는 요청은 일반 요청으로 남긴다")
    void logsActuatorLookalikeAsNormalRequest() throws Exception {
        var request = new MockHttpServletRequest("GET", "/actuatorx");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, respondWith(200));

        assertThat(appender.list).extracting(ILoggingEvent::getFormattedMessage)
            .satisfiesExactly(
                start -> assertThat(start).isEqualTo(">>> GET /actuatorx"),
                end -> assertThat(end).startsWith("<<< GET /actuatorx - 200"));
    }

    private static FilterChain respondWith(int status) {
        return (request, response) -> ((HttpServletResponse) response).setStatus(status);
    }
}
