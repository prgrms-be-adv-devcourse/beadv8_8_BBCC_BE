package com.bbcc.kidly.global.filter;

import static com.bbcc.kidly.support.TestApiController.ECHO_PATH;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.bbcc.kidly.global.security.TestMembers;
import com.bbcc.kidly.support.FunctionalTest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

@FunctionalTest
class RequestLoggingApiTest {

    private final Logger logger = (Logger) LoggerFactory.getLogger(RequestLoggingFilter.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>() {
        @Override
        protected void append(ILoggingEvent event) {
            // MDC는 필터가 끝나며 지워지므로 로그를 남긴 시점의 값을 붙잡아 둔다
            event.prepareForDeferredProcessing();
            super.append(event);
        }
    };

    @Autowired
    private RestTestClient client;

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
    @DisplayName("로그인 요청의 끝 로그에 회원 ID · 경로 패턴 · 가린 헤더와 본문을 남기고, 응답 본문은 그대로 보낸다")
    void logsRequestDetails() {
        var body = "{\"password\":\"pw\",\"name\":\"홍길동\",\"amount\":1000}";

        client.post().uri(ECHO_PATH)
            .headers(TestMembers.buyer(7L))
            // 서비스는 토큰을 보지 않지만, 들어온 Authorization 헤더는 로그에서 가린다
            .header(HttpHeaders.AUTHORIZATION, "Bearer secret-token")
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .exchange()
            .expectStatus().isOk()
            .expectBody().json(body);

        var end = appender.list.stream()
            .filter(event -> event.getFormattedMessage().startsWith("POST " + ECHO_PATH))
            .findFirst().orElseThrow();
        assertThat(end.getMDCPropertyMap()).containsEntry(RequestLoggingFilter.MEMBER_ID_MDC_KEY, "7");
        var fields = new LinkedHashMap<String, Object>();
        end.getKeyValuePairs().forEach(pair -> fields.put(pair.key, pair.value));
        assertThat(fields).containsEntry("http.route", ECHO_PATH)
            .containsEntry("http.request.body.content", "{\"password\":\"***\",\"name\":\"홍길동\",\"amount\":1000}")
            .containsEntry("http.response.body.content", "{\"password\":\"***\",\"name\":\"홍길동\",\"amount\":1000}");
        assertThat(fields.get("http.request.headers")).asInstanceOf(InstanceOfAssertFactories.MAP)
            .containsEntry("authorization", "***");
        assertThat(fields.get("client.ip")).isNotNull();
    }

    @Test
    @DisplayName("토큰 없는 요청은 회원 ID를 남기지 않는다")
    void skipsMemberIdWithoutToken() {
        client.post().uri(ECHO_PATH)
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("name", "홍길동"))
            .exchange()
            .expectStatus().isOk();

        var end = appender.list.stream()
            .filter(event -> event.getFormattedMessage().startsWith("POST " + ECHO_PATH))
            .findFirst().orElseThrow();
        assertThat(end.getMDCPropertyMap()).doesNotContainKey(RequestLoggingFilter.MEMBER_ID_MDC_KEY);
    }
}
