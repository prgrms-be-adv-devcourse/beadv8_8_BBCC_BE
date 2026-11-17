package com.bbcc.kidly.boundedcontext.order.out.ai;

import com.bbcc.kidly.boundedcontext.order.app.port.StuckOrderDiagnoser;
import com.bbcc.kidly.boundedcontext.order.domain.stuck.StuckOrderDiagnosis;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 멈춘 주문 진단의 프롬프트 만들기와 응답 해석(#926). 어느 제공자 어댑터든 이것을 같이 쓴다(제공자 무관).
 * 응답은 정해진 JSON 하나여야 한다: {"summary": 문자열, "recommendedAction": 문자열, "confidence": "LOW|MEDIUM|HIGH"}.
 * 칸이 빠졌거나 · 비었거나 · 다른 칸이 있거나 · 길이를 넘거나 · 확신도가 목록 밖이면 실패({@link AiDiagnosisException} BAD_RESPONSE).
 * 요청 값은 포트가 정한 주문 쪽 값뿐이고 글 칸은 서비스가 OrderTextGuard로 거른 값이다. null 칸은 빼고 보낸다. 프롬프트 · 응답 본문은 로그에 남기지 않는다.
 */
public final class AiDiagnosisPrompt {

    /**
     * 멈춘 종류(stuckKind, StuckOrderKind 이름)마다 무엇이 멈춘 것인지 · 흔한 원인 · 관리자가 할 수 있는 조치 한 줄(#1311).
     * 조치는 실제로 있는 관리자 API · 화면(결제 반영 실패 재처리 · 취소, 이벤트 처리 실패 재처리 · 종료, 주문 DLT 다시 처리 · 종료,
     * 격리 아웃박스 재시도, 주문 이력)과 시스템 밖 판매자 연락 · 개발 담당 확인만 쓴다. out은 app.stuck을 볼 수 없어 이름으로 두고,
     * 종류가 늘거나 줄면 테스트(AiDiagnosisPromptTest)가 StuckOrderKind와 맞지 않아 실패한다.
     */
    // SYSTEM이 이 값을 읽으므로 SYSTEM보다 먼저 선언해야 한다(정적 필드는 선언 순서로 초기화된다)
    static final Map<String, String> KIND_GUIDES = kindGuides();

    static final String SYSTEM = """
        너는 Kidly 이커머스 주문 운영을 돕는 진단 도우미다. 멈춘 주문 하나의 정보(JSON)를 받아 원인을 짧게 추정하고
        관리자가 할 조치를 제안한다. 조치는 관리자가 기존 관리자 API로 직접 한다(재처리 · 종료 · 취소 · 다시 보내기 등).
        정보에 없는 사실을 지어내지 말고, 모르면 확신도를 LOW로 둔다. 값이 없는 칸은 빠져 있다(알 수 없음).
        반드시 아래 키만 가진 JSON 객체 하나로 한국어로 답한다(다른 글 없이):
        {"summary": "원인 요약(300자 이내)", "recommendedAction": "권장 조치(300자 이내)", "confidence": "LOW|MEDIUM|HIGH"}
        """ + kindSection();
    private static final Set<String> FIELDS = Set.of("summary", "recommendedAction", "confidence");
    private static final Set<String> CONFIDENCES = Set.of("LOW", "MEDIUM", "HIGH");

    private final JsonMapper jsonMapper;

    private static Map<String, String> kindGuides() {
        Map<String, String> guides = new LinkedHashMap<>();
        guides.put("STOCK_CHECK_STALE", "재고 확인 결과가 안 와 결제대기에 멈춤 / 원인: 상품 서비스 중단, 재고 이벤트 유실 · 처리 실패 / "
            + "조치: 주문 DLT(상품 토픽) 다시 처리, 이벤트 처리 실패 재처리. 두면 결제 마감 뒤 만료 취소된다");
        guides.put("PAYMENT_EXPIRY_MISSED", "결제 마감이 지나도 만료 취소가 안 된 결제대기 / 원인: 만료 스케줄러 중단, "
            + "처리 안 된 결제 완료 이벤트가 남아 만료를 일부러 건너뜀 / 조치: 결제 이벤트 DLT · 이벤트 처리 실패 · 수신 상태 확인, "
            + "풀리면 자동 만료");
        guides.put("PAID_WITHOUT_DELIVERY", "결제완료인데 배송 없음 / 원인: 결제 반영 경로 이상 · 데이터 불일치 / "
            + "조치: 관리자 주문 이력 확인, 개발 담당 확인 요청(배송 생성 기능 없음)");
        guides.put("RECOVERY_PENDING_STALE", "예치금은 빠졌는데 결제 반영 실패 기록이 오래 방치 / 원인: 금액 · 구성 불일치, 동시 변경 / "
            + "조치: 결제 반영 실패 재처리(실패 코드 확인), 안 되면 취소(주문 취소 · 환불)");
        guides.put("SHIPPING_DELAYED", "판매자가 아직 발송 안 해(택배 인계 전) 배송이 READY에 머묾 / 원인: 판매자 출고 지연 · 재고 부족 / "
            + "조치: 판매자에게 발송을 요청하고, 어려우면 판매자에게 발송 전 취소를 요청");
        guides.put("AUTO_CONFIRM_MISSED", "배송 완료 뒤 자동 구매확정 기한이 지나도 미확정 / 원인: 자동 확정 스케줄러 중단 · 실패 / "
            + "조치: 스케줄러 상태 확인, 다시 돌면 자동 확정(관리자 확정 기능 없음)");
        guides.put("OUTBOX_STUCK", "주문 이벤트를 Kafka로 못 보냄 또는 격리 / 원인: Kafka 장애, 보낼 수 없는 메시지 / "
            + "조치: Kafka 상태 확인, 격리는 원인 해결 뒤 격리 아웃박스 재시도(버리는 기능 없음)");
        return Collections.unmodifiableMap(guides);
    }

    private static String kindSection() {
        StringBuilder section = new StringBuilder("stuckKind별 뜻 / 원인 / 관리자 조치:\n");
        KIND_GUIDES.forEach((kind, guide) -> section.append("- ").append(kind).append(": ").append(guide).append('\n'));
        return section.append("""
            위 조치(관리자 API · 화면, 판매자 연락, 개발 담당 확인)만 권한다. 택배사 연락 · 재발송 · 외부 문의처럼 Kidly에 없는 기능은 \
            권하지 않는다. stuckKind가 목록에 없거나 정보로 원인을 판단할 수 없으면 확신도 LOW.
            응답은 위 JSON 형식(summary · recommendedAction · confidence)만 낸다.
            """).toString();
    }

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
        // 값이 없거나 걸러진(형식 밖 자유 문구) 칸은 빼고 보낸다
        return "멈춘 주문 정보(JSON):\n" + jsonMapper.writeValueAsString(withoutNulls(jsonMapper.valueToTree(body)));
    }

    private static JsonNode withoutNulls(JsonNode node) {
        if (node instanceof ObjectNode object) {
            List<String> nulls = object.propertyNames().stream().filter(name -> object.get(name).isNull()).toList();
            nulls.forEach(object::remove);
            object.forEach(AiDiagnosisPrompt::withoutNulls);
        } else if (node instanceof ArrayNode array) {
            array.forEach(AiDiagnosisPrompt::withoutNulls);
        }
        return node;
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
