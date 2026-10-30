package com.bbcc.kidly.boundedcontext.order.app;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.boot.logging.logback.StructuredLogEncoder;
import org.springframework.core.env.Environment;
import org.springframework.core.env.StandardEnvironment;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 운영 콘솔 로그(logback-spring.xml prod: StructuredLogEncoder, format ecs)가 OrderLogContext의 MDC를 JSON 칸으로 넣는지(#603).
 * 운영 설정과 같은 인코더 · 형식을 이 테스트 안의 로거 컨텍스트에서만 쓴다(공용 로그 설정은 그대로).
 * application-prod.yml은 logging.structured.json.context를 바꾸지 않으므로 기본값(포함 · 최상위 칸)과 같다.
 */
class OrderLogContextEcsJsonTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    @DisplayName("ECS JSON 한 줄에 eventId · eventType · correlationId · traceId가 최상위 칸으로 들어간다")
    void mdcBecomesJsonFields() {
        LoggerContext context = new LoggerContext();
        context.putObject(Environment.class.getName(), new StandardEnvironment());
        StructuredLogEncoder encoder = new StructuredLogEncoder();
        encoder.setContext(context);
        encoder.setFormat("ecs");
        encoder.setCharset(UTF_8);
        encoder.start();
        LoggingEvent event = new LoggingEvent(getClass().getName(), context.getLogger("order"), Level.INFO,
            "결제완료: orderId=42", null, null);
        try (var ignored = OrderLogContext.open("e-1", "payment.completed", "42")) {
            event.setMDCPropertyMap(MDC.getCopyOfContextMap());
        }

        JsonNode json = JsonMapper.builder().build().readTree(new String(encoder.encode(event), UTF_8));

        assertThat(json.get("eventId").asString()).isEqualTo("e-1");
        assertThat(json.get("eventType").asString()).isEqualTo("payment.completed");
        assertThat(json.get("correlationId").asString()).isEqualTo("42");
        assertThat(json.get("traceId").asString()).isEqualTo("42");
        assertThat(json.get("message").asString()).isEqualTo("결제완료: orderId=42");
    }
}
