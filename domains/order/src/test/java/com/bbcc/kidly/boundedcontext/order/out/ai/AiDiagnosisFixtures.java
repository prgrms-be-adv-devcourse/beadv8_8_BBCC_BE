package com.bbcc.kidly.boundedcontext.order.out.ai;

import com.bbcc.kidly.boundedcontext.order.app.port.StuckOrderDiagnoser;
import com.bbcc.kidly.boundedcontext.order.out.config.OrderAiProperties;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

final class AiDiagnosisFixtures {

    static final String KEY = "gsk_test_secret_key_123";

    private AiDiagnosisFixtures() {
    }

    static StuckOrderDiagnoser.Request request() {
        LocalDateTime at = LocalDateTime.of(2026, 11, 6, 9, 0);
        return new StuckOrderDiagnoser.Request("PAID_WITHOUT_DELIVERY", "ORDER", "PAID", at.plusHours(1), "PAID",
            List.of(new StuckOrderDiagnoser.HistoryLine("ORDER", null, "PENDING_PAYMENT", null, "BUYER",
                    "POST /api/v1/orders", at),
                new StuckOrderDiagnoser.HistoryLine("ORDER", "PENDING_PAYMENT", "PAID", null, "EVENT",
                    "payment.completed", at.plusMinutes(1))),
            List.of(new StuckOrderDiagnoser.RecordLine("PAYMENT_RECOVERY", "payment.completed", "PENDING",
                "delivery-failed", 2)));
    }

    static OrderAiProperties properties(String baseUrl, String model, String apiKey) {
        return new OrderAiProperties(true, OrderAiProperties.Provider.OPENAI_COMPATIBLE, baseUrl, model, apiKey,
            Duration.ofSeconds(1), Duration.ofSeconds(1), 800, new OrderAiProperties.CircuitBreaker(4, 2, 50,
                Duration.ofSeconds(10), 50, Duration.ofMinutes(10), 1));
    }

    static String completion(String content) {
        return """
            {"id":"x","object":"chat.completion","choices":[{"index":0,"message":{"role":"assistant","content":%s},
             "finish_reason":"stop"}]}""".formatted(quote(content));
    }

    static String quote(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }

    static final String GOOD = """
        {"summary":"결제는 끝났지만 배송 건이 만들어지지 않았습니다","recommendedAction":"결제 복구 기록을 재처리하세요","confidence":"MEDIUM"}""";
}
