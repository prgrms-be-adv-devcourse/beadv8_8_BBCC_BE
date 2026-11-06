package com.bbcc.kidly.boundedcontext.order.out.ai;

import com.bbcc.kidly.boundedcontext.order.app.port.StuckOrderDiagnoser;
import com.bbcc.kidly.boundedcontext.order.domain.stuck.StuckOrderDiagnosis;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 멈춘 주문 진단의 프롬프트 만들기와 응답 해석(#926). 어느 제공자 어댑터든 이것을 같이 쓴다(제공자 무관).
 * 응답은 정해진 JSON 하나여야 한다: {"summary": 문자열, "recommendedAction": 문자열, "confidence": "LOW|MEDIUM|HIGH"}.
 * 칸이 빠졌거나 · 비었거나 · 다른 칸이 있거나 · 길이를 넘거나 · 확신도가 목록 밖이면 실패({@link AiDiagnosisException} BAD_RESPONSE).
 * 요청 값은 포트가 정한 주문 쪽 값뿐이라 개인정보가 없다. 프롬프트 · 응답 본문은 로그에 남기지 않는다.
 */
public final class AiDiagnosisPrompt {

    static final String SYSTEM = """
        너는 Kidly 이커머스 주문 운영을 돕는 진단 도우미다. 멈춘 주문 하나의 정보(JSON)를 받아 원인을 짧게 추정하고
        관리자가 할 조치를 제안한다. 조치는 관리자가 기존 관리자 API로 직접 한다(재처리 · 종료 · 취소 · 다시 보내기 등).
        정보에 없는 사실을 지어내지 말고, 모르면 확신도를 LOW로 둔다.
        반드시 아래 키만 가진 JSON 객체 하나로 한국어로 답한다(다른 글 없이):
        {"summary": "원인 요약(300자 이내)", "recommendedAction": "권장 조치(300자 이내)", "confidence": "LOW|MEDIUM|HIGH"}
        """;
    private static final Set<String> FIELDS = Set.of("summary", "recommendedAction", "confidence");
    private static final Set<String> CONFIDENCES = Set.of("LOW", "MEDIUM", "HIGH");

    private final JsonMapper jsonMapper;

    public AiDiagnosisPrompt(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    String system() {
        return SYSTEM;
    }

    /**
     * 사용자 메시지: 진단할 멈춘 주문 정보(JSON).
     */
    String user(StuckOrderDiagnoser.Request request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("stuckKind", request.stuckKind());
        body.put("targetType", request.targetType());
        body.put("targetStatus", request.targetStatus());
        body.put("stuckSince", request.since());
        body.put("orderStatus", request.orderStatus());
        body.put("history", request.history());
        body.put("records", request.records());
        return "멈춘 주문 정보(JSON):\n" + jsonMapper.writeValueAsString(body);
    }

    /**
     * 모델이 돌려준 본문(JSON 문자열)을 해석한다.
     *
     * @throws AiDiagnosisException 형식이 틀리면 BAD_RESPONSE(메시지에 본문을 넣지 않는다)
     */
    StuckOrderDiagnoser.Diagnosis parse(String content, String provider, String model) {
        if (content == null || content.isBlank()) {
            throw AiDiagnosisException.badResponse("empty");
        }
        JsonNode node;
        try {
            node = jsonMapper.readTree(content.strip());
        } catch (JacksonException e) {
            throw AiDiagnosisException.badResponse("not-json");
        }
        if (node == null || !node.isObject() || !FIELDS.equals(Set.copyOf(node.propertyNames()))) {
            throw AiDiagnosisException.badResponse("fields");
        }
        String summary = text(node, "summary");
        String action = text(node, "recommendedAction");
        String confidence = text(node, "confidence");
        if (!CONFIDENCES.contains(confidence)) {
            throw AiDiagnosisException.badResponse("confidence");
        }
        return StuckOrderDiagnoser.Diagnosis.diagnosed(summary, action,
            StuckOrderDiagnoser.Confidence.valueOf(confidence), provider, model);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isString() || value.asString().isBlank()
            || value.asString().length() > StuckOrderDiagnosis.TEXT_MAX) {
            throw AiDiagnosisException.badResponse(field);
        }
        return value.asString().strip();
    }

    /**
     * Chat Completions 메시지 두 개(system · user). OpenAI 호환이 아닌 어댑터도 같은 글을 쓴다.
     */
    List<Map<String, String>> messages(StuckOrderDiagnoser.Request request) {
        return List.of(Map.of("role", "system", "content", system()), Map.of("role", "user", "content", user(request)));
    }
}
