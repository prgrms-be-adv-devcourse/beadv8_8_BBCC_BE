package com.bbcc.kidly.boundedcontext.chatbot.app;

import com.bbcc.kidly.boundedcontext.chatbot.app.port.ChatbotAi.Tool;
import com.bbcc.kidly.shared.order.dto.MemberOrderSummary;
import com.bbcc.kidly.shared.order.out.MemberOrderHistory;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * 모델이 부르는 내 주문 조회 도구. 질문 하나마다 로그인한 회원으로 만들어, 모델은 회원 ID를 보지도 고르지도 못한다.
 * 모델에는 상품 이름 · 옵션 · 수량 · 상태 · 택배사 · 날짜만 준다. 로그에는 회원 ID와 주문 내용을 남기지 않는다.
 */
@Slf4j
final class MyOrdersTool {

    static final String NAME = "getMyRecentOrders";
    static final String UNAVAILABLE = "주문 정보를 지금 볼 수 없습니다. 주문을 확인하지 못했다고 짧게 안내하고, 주문 상세 화면에서 확인하거나 "
        + "잠시 뒤 다시 물어 달라고 하세요.";

    private static final int DEFAULT_LIMIT = 5;
    private static final int MAX_LIMIT = 10;
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final String DESCRIPTION = """
        로그인한 회원 본인의 최근 주문을 최근 것부터 봅니다. 주문마다 주문 시각 · 상태와 상품별 옵션 · 수량 · 상태 · 배송(상태 · 택배사 ·
        발송 시각 · 배송 완료 시각)을 줍니다. 주문 · 결제 · 배송 상황을 묻는 질문이면 이 도구를 부르세요. today는 오늘 날짜입니다.
        """;
    private static final String INPUT_SCHEMA = """
        {
          "type": "object",
          "properties": {
            "limit": {"type": "integer", "description": "볼 주문 수(1~10). 보내지 않으면 5. 오래전 주문을 찾을 때만 늘리세요"}
          },
          "additionalProperties": false
        }
        """;

    // 모르는 값(주문 쪽에 새로 생긴 상태 · 택배사)은 그대로 준다
    private static final Map<String, String> ORDER_STATUS = Map.of(
        "PENDING_PAYMENT", "결제 대기",
        "PAID", "결제 완료",
        "CANCELLED", "주문 취소");
    private static final Map<String, String> ITEM_STATUS = Map.of(
        "ORDERED", "주문됨",
        "CONFIRMED", "구매 확정",
        "CANCELLED", "취소됨");
    private static final Map<String, String> DELIVERY_STATUS = Map.of(
        "READY", "배송 준비 중",
        "SHIPPING", "배송 중",
        "DELIVERED", "배송 완료",
        "CANCELLED", "배송 취소");
    private static final Map<String, String> CARRIER = Map.of(
        "CJ", "CJ대한통운",
        "EPOST", "우체국택배",
        "HANJIN", "한진택배",
        "LOTTE", "롯데택배",
        "LOGEN", "로젠택배",
        "DUMMY", "테스트택배");

    private final MemberOrderHistory memberOrderHistory;
    private final Long memberId;

    MyOrdersTool(MemberOrderHistory memberOrderHistory, Long memberId) {
        this.memberOrderHistory = memberOrderHistory;
        this.memberId = memberId;
    }

    Tool<Input> tool() {
        return new Tool<>(NAME, DESCRIPTION, INPUT_SCHEMA, Input.class, this::lookup);
    }

    private Object lookup(Input input) {
        List<MemberOrderSummary> orders;
        try {
            orders = memberOrderHistory.recent(memberId, input == null ? DEFAULT_LIMIT : input.limitInRange());
        } catch (RuntimeException e) {
            log.warn("챗봇 주문 조회 실패: cause={}", e.getClass().getSimpleName());
            return new Failure(UNAVAILABLE);
        }
        log.info("챗봇 주문 조회: orders={}", orders.size());
        return new Found(LocalDate.now().toString(), orders.stream().map(FoundOrder::from).toList());
    }

    /**
     * 모델이 보낸 인자. 범위 밖 limit은 고쳐 쓴다.
     */
    record Input(Integer limit) {

        int limitInRange() {
            if (limit == null) {
                return DEFAULT_LIMIT;
            }
            return Math.clamp(limit, 1, MAX_LIMIT);
        }
    }

    record Found(String today, List<FoundOrder> orders) {
    }

    record FoundOrder(Long orderId, String orderedAt, String status, List<FoundItem> items) {

        static FoundOrder from(MemberOrderSummary order) {
            return new FoundOrder(order.orderId(), format(order.orderedAt()), label(ORDER_STATUS, order.status()),
                order.items().stream().map(FoundItem::from).toList());
        }
    }

    record FoundItem(String productName, String optionName, int quantity, String status, FoundDelivery delivery) {

        static FoundItem from(MemberOrderSummary.Item item) {
            return new FoundItem(item.productName(), item.optionName(), item.quantity(),
                label(ITEM_STATUS, item.status()), FoundDelivery.from(item.delivery()));
        }
    }

    record FoundDelivery(String status, String carrier, String shippedAt, String deliveredAt) {

        static FoundDelivery from(MemberOrderSummary.Delivery delivery) {
            if (delivery == null) {
                return null;
            }
            return new FoundDelivery(label(DELIVERY_STATUS, delivery.status()), label(CARRIER, delivery.carrier()),
                format(delivery.shippedAt()), format(delivery.deliveredAt()));
        }
    }

    record Failure(String error) {
    }

    private static String label(Map<String, String> labels, String name) {
        return name == null ? null : labels.getOrDefault(name, name);
    }

    private static String format(LocalDateTime time) {
        return time == null ? null : time.format(DATE_TIME);
    }
}
