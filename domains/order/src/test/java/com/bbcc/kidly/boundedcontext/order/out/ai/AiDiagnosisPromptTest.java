package com.bbcc.kidly.boundedcontext.order.out.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.order.app.port.StuckOrderDiagnoser;
import com.bbcc.kidly.boundedcontext.order.app.stuck.StuckOrderKind;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

/**
 * 제공자 무관 프롬프트 · 응답 해석(#926): 정해진 JSON만 받고 틀리면 BAD_RESPONSE.
 * 시스템 프롬프트의 종류별 뜻 · 원인 · 조치 줄(#1311)은 StuckOrderKind와 하나씩 맞아야 한다(종류가 늘거나 줄면 실패).
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

    @Test
    @DisplayName("종류별 안내 줄(#1311)은 StuckOrderKind 7개와 같은 이름 · 같은 순서다: 종류가 늘거나 줄면 여기서 실패한다")
    void kindGuidesMatchStuckOrderKinds() {
        assertThat(AiDiagnosisPrompt.KIND_GUIDES.keySet())
            .containsExactlyElementsOf(Arrays.stream(StuckOrderKind.values()).map(Enum::name).toList());
    }

    @Test
    @DisplayName("시스템 프롬프트에 종류마다 한 줄씩 뜻 / 원인 / 조치가 들어간다")
    void systemHasLinePerKind() {
        assertThat(StuckOrderKind.values()).allSatisfy(kind -> {
            String guide = AiDiagnosisPrompt.KIND_GUIDES.get(kind.name());
            assertThat(guide).contains(" / 원인: ", " / 조치: ").doesNotContain("\n");
            assertThat(prompt.system()).contains("- " + kind.name() + ": " + guide + "\n");
        });
    }

    @Test
    @DisplayName("조치는 실제 관리자 기능 이름을 쓴다(핵심어): 발송 지연은 판매자에게 요청, 만료 누락은 결제 이벤트 수신 확인")
    void guidesUseExistingActions() {
        assertThat(action("SHIPPING_DELAYED")).contains("판매자에게 발송", "판매자에게 발송 전 취소");
        assertThat(guide("PAYMENT_EXPIRY_MISSED")).contains("결제 완료 이벤트", "건너뜀");
        assertThat(action("PAYMENT_EXPIRY_MISSED")).contains("결제 이벤트 DLT", "이벤트 처리 실패", "수신 상태")
            .doesNotContain("결제 반영 실패");
        assertThat(action("RECOVERY_PENDING_STALE")).contains("결제 반영 실패 재처리", "취소");
        assertThat(action("STOCK_CHECK_STALE")).contains("주문 DLT", "다시 처리", "이벤트 처리 실패 재처리");
        assertThat(action("OUTBOX_STUCK")).contains("격리 아웃박스 재시도");
        assertThat(action("PAID_WITHOUT_DELIVERY")).contains("주문 이력");
    }

    @Test
    @DisplayName("어느 종류의 조치에도 없는 기능(택배사 · 재발송)이 없다")
    void actionsHaveNoMissingFeatures() {
        assertThat(AiDiagnosisPrompt.KIND_GUIDES.keySet())
            .allSatisfy(kind -> assertThat(action(kind)).doesNotContain("택배사", "재발송"));
    }

    @Test
    @DisplayName("없는 기능을 권하지 말라는 안내 · 판단할 수 없으면 LOW 규칙이 있고, 프롬프트 맨 끝은 JSON 형식만 내라는 줄이다")
    void systemForbidsMissingFeaturesAndEndsWithFormat() {
        assertThat(prompt.system()).contains("택배사 연락 · 재발송", "권하지 않는다", "판단할 수 없으면", "LOW");
        assertThat(prompt.system().strip().lines().toList().getLast()).contains("JSON 형식", "만 낸다");
    }

    @Test
    @DisplayName("종류 안내 줄(\"- 종류: …\")은 합쳐 900자 이하, 시스템 프롬프트 전체는 1,600자 이하다(한 실행 여러 건 호출의 분당 토큰 한도)")
    void promptStaysSmall() {
        int guideLines = AiDiagnosisPrompt.KIND_GUIDES.entrySet().stream()
            .mapToInt(entry -> ("- " + entry.getKey() + ": " + entry.getValue()).length())
            .sum();
        assertThat(guideLines).isLessThanOrEqualTo(900);
        assertThat(prompt.system()).hasSizeLessThanOrEqualTo(1_600);
    }

    private static String guide(String kind) {
        return AiDiagnosisPrompt.KIND_GUIDES.get(kind);
    }

    private static String action(String kind) {
        String guide = guide(kind);
        return guide.substring(guide.indexOf(" / 조치: "));
    }
}
