package com.bbcc.kidly.boundedcontext.order.out.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.order.app.port.StuckOrderDiagnoser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

/**
 * 제공자 무관 프롬프트 · 응답 해석(#926): 정해진 JSON만 받고 틀리면 BAD_RESPONSE.
 */
class AiDiagnosisPromptTest {

    private final AiDiagnosisPrompt prompt = new AiDiagnosisPrompt(JsonMapper.shared());

    @Test
    @DisplayName("정해진 JSON이면 요약 · 권장 조치 · 확신도와 제공자 · 모델을 돌려준다")
    void parsesFixedJson() {
        StuckOrderDiagnoser.Diagnosis diagnosis = prompt.parse(AiDiagnosisFixtures.GOOD, "api.groq.com", "m");

        assertThat(diagnosis).isEqualTo(StuckOrderDiagnoser.Diagnosis.diagnosed(
            "결제는 끝났지만 배송 건이 만들어지지 않았습니다", "결제 복구 기록을 재처리하세요",
            StuckOrderDiagnoser.Confidence.MEDIUM, "api.groq.com", "m"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "",
        "결제가 끝났습니다",
        "[]",
        "{\"summary\":\"a\",\"recommendedAction\":\"b\"}",
        "{\"summary\":\"a\",\"recommendedAction\":\"b\",\"confidence\":\"HIGH\",\"extra\":1}",
        "{\"summary\":\" \",\"recommendedAction\":\"b\",\"confidence\":\"HIGH\"}",
        "{\"summary\":\"a\",\"recommendedAction\":1,\"confidence\":\"HIGH\"}",
        "{\"summary\":\"a\",\"recommendedAction\":\"b\",\"confidence\":\"high\"}",
        "{\"summary\":\"a\",\"recommendedAction\":\"b\",\"confidence\":\"VERY_HIGH\"}"
    })
    @DisplayName("형식이 틀리면(JSON 아님 · 칸 빠짐 · 칸 더 있음 · 빈 값 · 타입 · 확신도 목록 밖) BAD_RESPONSE, 메시지에 본문이 없다")
    void rejectsWrongFormat(String content) {
        assertThatThrownBy(() -> prompt.parse(content, "p", "m"))
            .isInstanceOfSatisfying(AiDiagnosisException.class, e -> {
                assertThat(e.reason()).isEqualTo(AiDiagnosisException.BAD_RESPONSE);
                if (!content.isBlank()) {
                    assertThat(e.getMessage()).doesNotContain(content);
                }
            });
    }

    @Test
    @DisplayName("1000자를 넘는 요약은 BAD_RESPONSE")
    void rejectsTooLong() {
        String content = "{\"summary\":\"" + "가".repeat(1001)
            + "\",\"recommendedAction\":\"b\",\"confidence\":\"LOW\"}";

        assertThatThrownBy(() -> prompt.parse(content, "p", "m")).isInstanceOf(AiDiagnosisException.class);
    }

    @Test
    @DisplayName("사용자 메시지에는 주문 쪽 값(종류 · 상태 · 이력 · 기록)만 있고 개인정보 칸이 없으며, system 메시지가 JSON 형식을 요구한다")
    void userMessageHasOnlyOrderValues() {
        String user = prompt.user(AiDiagnosisFixtures.request());

        assertThat(user).contains("PAID_WITHOUT_DELIVERY", "payment.completed", "delivery-failed",
            "\"stuckSince\":\"2026-11-06T10:00:00\"");
        assertThat(user).doesNotContain("recipient", "phone", "address", "zipCode", "nickname", "name\"", "child",
            "memberId", "actorId", "height", "weight");
        assertThat(prompt.system()).contains("JSON", "summary", "recommendedAction", "confidence");
    }
}
