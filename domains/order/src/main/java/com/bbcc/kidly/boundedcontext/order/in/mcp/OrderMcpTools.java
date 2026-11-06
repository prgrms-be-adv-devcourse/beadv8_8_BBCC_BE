package com.bbcc.kidly.boundedcontext.order.in.mcp;

import com.bbcc.kidly.boundedcontext.order.app.deadletter.OrderDeadLetterService;
import com.bbcc.kidly.boundedcontext.order.app.order.OrderEventFailureService;
import com.bbcc.kidly.boundedcontext.order.app.order.OrderQueryService;
import com.bbcc.kidly.boundedcontext.order.app.order.PaymentRecoveryService;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.OrderStatusHistoryResult;
import com.bbcc.kidly.boundedcontext.order.app.outbox.OrderOutboxAdminService;
import com.bbcc.kidly.boundedcontext.order.app.stuck.StuckOrderKind;
import com.bbcc.kidly.boundedcontext.order.app.stuck.StuckOrderService;
import com.bbcc.kidly.boundedcontext.order.in.web.admin.dto.OrderEventFailureResponse;
import com.bbcc.kidly.boundedcontext.order.in.web.admin.dto.OrderStatusHistoryResponse;
import com.bbcc.kidly.boundedcontext.order.in.web.admin.dto.OutboxQuarantineResponse;
import com.bbcc.kidly.boundedcontext.order.in.web.admin.dto.PaymentRecoveryResponse;
import com.bbcc.kidly.boundedcontext.order.in.web.admin.dto.StuckOrderResponse;
import com.bbcc.kidly.global.error.BusinessException;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import io.modelcontextprotocol.spec.McpSchema.ToolAnnotations;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import tools.jackson.databind.json.JsonMapper;

/**
 * 주문 운영 조회 MCP 도구(#925, 읽기 전용). 관리자 조회 API(web.admin)가 쓰는 app 조회 서비스를 그대로 부르고, 응답은 관리자 API와 같은 칸을 쓴다.
 * 개인정보(배송지 · 연락처 · 수령인 · 닉네임 · 자녀 정보)는 이 응답들에 없다. DLT 상세는 원본 본문(payload) · 관리자 메모 · 예외 메시지 · 스택을 빼고 메타데이터(예외 종류만)를 준다.
 * 자유 문구가 될 수 있는 칸(아웃박스 lastError · 이력 reason/source · 실패 코드 · 받은 메시지의 키/ID 등)은 OrderMcpViews ·
 * OrderMcpDeadLetter가 OrderTextGuard로 걸러 코드 · 예외 클래스 이름 · 경로 틀 · 이벤트 타입 · ID 형식만 내보낸다.
 * 목록은 size를 max-results 이하로 줄인다. 조작(재처리 · 종료 · 취소) 도구는 없다.
 * 결과는 JSON 텍스트 한 덩어리. 업무 오류(없는 주문 등)와 잘못된 인자는 isError로 돌려준다(HTTP는 200).
 */
@Slf4j
public class OrderMcpTools {

    static final int DEFAULT_SIZE = 20;
    private static final ToolAnnotations READ_ONLY = ToolAnnotations.builder()
        .readOnlyHint(true).destructiveHint(false).idempotentHint(true).openWorldHint(false).build();
    private static final Map<String, Object> PAGE = Map.of("type", "integer", "minimum", 0,
        "description", "0부터 시작하는 페이지 번호. 기본 0");
    private static final Map<String, Object> ID = Map.of("type", List.of("string", "integer"),
        "description", "ID(숫자 문자열 또는 정수)");

    private final StuckOrderService stuckOrderService;
    private final OrderQueryService orderQueries;
    private final OrderDeadLetterService deadLetterService;
    private final OrderEventFailureService failureService;
    private final PaymentRecoveryService recoveryService;
    private final OrderOutboxAdminService outboxService;
    private final int maxResults;
    private final JsonMapper jsonMapper;

    public OrderMcpTools(StuckOrderService stuckOrderService, OrderQueryService orderQueries,
        OrderDeadLetterService deadLetterService, OrderEventFailureService failureService,
        PaymentRecoveryService recoveryService, OrderOutboxAdminService outboxService, int maxResults,
        JsonMapper jsonMapper) {
        this.stuckOrderService = stuckOrderService;
        this.orderQueries = orderQueries;
        this.deadLetterService = deadLetterService;
        this.failureService = failureService;
        this.recoveryService = recoveryService;
        this.outboxService = outboxService;
        this.maxResults = maxResults;
        this.jsonMapper = jsonMapper;
    }

    public List<SyncToolSpecification> specifications() {
        List<String> kinds = Arrays.stream(StuckOrderKind.values()).map(Enum::name).toList();
        return List.of(
            tool("stuck_orders_summary", "멈춘 주문 종류별 건수(0 포함). 한 주문이 여러 종류에 들 수 있어 합은 멈춘 주문 수가 아니다.",
                Map.of(), List.of(), args -> stuckSummary()),
            tool("stuck_orders", "그 종류의 멈춘 대상을 오래된 것부터. kind는 stuck_orders_summary의 키 중 하나.",
                Map.of("kind", Map.of("type", "string", "enum", kinds), "page", PAGE, "size", size()),
                List.of("kind"), args -> page(stuckOrderService.getStuckOrders(args.text("kind"), LocalDateTime.now(),
                    args.page(), args.size()).map(StuckOrderResponse::from).map(OrderMcpViews::stuck))),
            tool("order_timeline", "주문 · 주문상품 · 배송 · 결제 복구의 상태 변경 이력(오래된 것부터). 이벤트가 바꾼 상태는 "
                    + "actorType EVENT · source에 이벤트 타입. 너무 길면 최근 max-results 줄만(truncated true).",
                Map.of("orderId", ID), List.of("orderId"), args -> timeline(args.id("orderId"))),
            tool("dead_letters", "주문이 받은 Kafka DLT 메시지 목록. 결제 토픽이 먼저, 그 안은 최근 받은 것부터. status를 주지 않으면 OPEN만.",
                Map.of("status", Map.of("type", "string", "enum", List.of("OPEN", "REPROCESSED", "CLOSED")),
                    "failureType", Map.of("type", "string",
                        "enum", List.of("READ_FAILED", "STORE_FAILED", "PROCESS_FAILED")),
                    "eventType", Map.of("type", "string", "description", "예: payment.completed"),
                    "page", PAGE, "size", size()),
                List.of(), args -> page(deadLetterService.getDeadLetters(args.text("status"),
                    args.text("failureType"), args.text("eventType"), args.page(), args.size())
                    .map(OrderMcpViews::deadLetter))),
            tool("dead_letter_detail", "DLT 메시지 하나의 메타데이터 · 실패 정보 · 관련 주문의 지금 상태 · 권장 조치. 원본 본문은 주지 않는다.",
                Map.of("deadLetterId", ID), List.of("deadLetterId"),
                args -> OrderMcpDeadLetter.from(deadLetterService.getDeadLetter(args.id("deadLetterId")))),
            tool("event_failures", "재고 · 결제 이벤트 처리 실패 기록. 최근 기록부터.",
                Map.of("status", Map.of("type", "string", "enum", List.of("PENDING", "RECOVERED", "CANCELLED")),
                    "page", PAGE, "size", size()),
                List.of(), args -> page(failureService.getFailures(args.text("status"), args.page(), args.size())
                    .map(OrderEventFailureResponse::from).map(OrderMcpViews::eventFailure))),
            tool("payment_recoveries", "결제 완료를 주문에 반영하지 못한 복구 기록. 최근 기록부터.",
                Map.of("status", Map.of("type", "string",
                        "enum", List.of("PENDING", "RECOVERED", "CANCELLED", "NOT_RETRYABLE")),
                    "page", PAGE, "size", size()),
                List.of(), args -> page(recoveryService.getRecoveries(args.text("status"), args.page(), args.size())
                    .map(PaymentRecoveryResponse::from).map(OrderMcpViews::paymentRecovery))),
            tool("outbox_quarantined", "보내지 못하고 격리된 주문 아웃박스 메시지(본문 없음).",
                Map.of("page", PAGE, "size", size()),
                List.of(), args -> page(outboxService.findQuarantined(args.page(), args.size())
                    .map(OutboxQuarantineResponse::from).map(OrderMcpViews::outbox))));
    }

    private Map<String, Long> stuckSummary() {
        Map<String, Long> summary = new LinkedHashMap<>();
        stuckOrderService.getSummary(LocalDateTime.now()).forEach((kind, count) -> summary.put(kind.name(), count));
        return summary;
    }

    private OrderMcpTimeline timeline(Long orderId) {
        List<OrderStatusHistoryResult> history = orderQueries.getHistory(orderId);
        int from = Math.max(0, history.size() - maxResults);
        return new OrderMcpTimeline(String.valueOf(orderId), history.size(), from > 0,
            history.subList(from, history.size()).stream().map(OrderStatusHistoryResponse::from)
                .map(OrderMcpViews::history).toList());
    }

    private <T> OrderMcpPage<T> page(Page<T> page) {
        return new OrderMcpPage<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(),
            page.getTotalPages(), page.hasNext());
    }

    private Map<String, Object> size() {
        return Map.of("type", "integer", "minimum", 1,
            "description", "한 페이지 줄 수. 기본 " + Math.min(DEFAULT_SIZE, maxResults) + ", 최대 " + maxResults
                + "(더 크면 최대로 줄인다)");
    }

    private SyncToolSpecification tool(String name, String description, Map<String, Object> properties,
        List<String> required, Function<OrderMcpArguments, Object> handler) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", required);
        schema.put("additionalProperties", false);
        Tool tool = Tool.builder(name, schema)
            .description(description)
            .annotations(READ_ONLY)
            .build();
        return SyncToolSpecification.builder()
            .tool(tool)
            .callHandler((context, request) -> call(name, request, handler))
            .build();
    }

    private CallToolResult call(String name, CallToolRequest request, Function<OrderMcpArguments, Object> handler) {
        try {
            Object result = handler.apply(new OrderMcpArguments(request.arguments(), maxResults));
            return CallToolResult.builder().addTextContent(jsonMapper.writeValueAsString(result)).isError(false)
                .build();
        } catch (BusinessException e) {
            return error(e.getErrorCode() instanceof Enum<?> code ? code.name() : "BUSINESS_ERROR",
                e.getErrorCode().title());
        } catch (OrderMcpArguments.InvalidArgumentException e) {
            return error("INVALID_ARGUMENT", e.getMessage());
        } catch (RuntimeException e) {
            // 인자 값 · 응답 본문은 남기지 않는다(개인정보가 섞일 수 있음)
            log.warn("주문 MCP 도구 실패: tool={}, exception={}", name, e.getClass().getName());
            return error("INTERNAL_ERROR", "조회하지 못했습니다");
        }
    }

    private CallToolResult error(String code, String message) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("error", code);
        body.put("message", message);
        return CallToolResult.builder().addTextContent(jsonMapper.writeValueAsString(body)).isError(true).build();
    }
}
