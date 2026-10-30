package com.bbcc.kidly.boundedcontext.order.app;

import com.bbcc.kidly.global.filter.RequestLoggingFilter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.MDC;

/**
 * 주문 비동기 처리(Kafka 수신 · 아웃박스 릴레이 · 주문 스케줄러)에서 메시지 · 대상 하나를 처리하는 동안 로그에 붙일 추적 값(MDC, #603).
 * 운영 JSON 로그는 MDC를 칸으로 넣으므로 주문 하나의 흐름을 같은 값으로 찾을 수 있다.
 * {@code try (var ignored = OrderLogContext.open(eventId, eventType, correlationId)) { … }}
 * <ul>
 *   <li>eventId · eventType · correlationId를 넣는다. 없는 값(null)은 넣지 않고, 바깥에 같은 키가 있어도 이 범위에서는 가린다</li>
 *   <li>요청 밖이라 traceId가 비어 있으면 correlationId를 넣는다. 이미 있으면(HTTP 요청 안) 그대로 둔다</li>
 *   <li>닫으면(예외로 끝나도) 네 키를 연 때의 값으로 되돌린다: 다른 메시지 · 다른 도메인 · 요청 로그에 남지 않는다</li>
 * </ul>
 * 개인정보 · 메시지 본문은 넣지 않는다(헤더 · 키 · 주문 ID만).
 */
public final class OrderLogContext implements AutoCloseable {

    public static final String EVENT_ID = "eventId";
    public static final String EVENT_TYPE = "eventType";
    public static final String CORRELATION_ID = "correlationId";
    // 공용 요청 필터가 쓰는 키(요청마다 넣고 요청 끝에 지운다)
    private static final String TRACE_ID = RequestLoggingFilter.TRACE_ID_MDC_KEY;
    private static final List<String> KEYS = List.of(EVENT_ID, EVENT_TYPE, CORRELATION_ID, TRACE_ID);

    // 연 때의 값. null이면 그 키가 없었다
    private final Map<String, String> previous;

    private OrderLogContext(Map<String, String> previous) {
        this.previous = previous;
    }

    public static OrderLogContext open(String eventId, String eventType, String correlationId) {
        Map<String, String> previous = new LinkedHashMap<>();
        KEYS.forEach(key -> previous.put(key, MDC.get(key)));
        put(EVENT_ID, eventId);
        put(EVENT_TYPE, eventType);
        put(CORRELATION_ID, correlationId);
        if (MDC.get(TRACE_ID) == null && correlationId != null) {
            MDC.put(TRACE_ID, correlationId);
        }
        return new OrderLogContext(previous);
    }

    @Override
    public void close() {
        previous.forEach(OrderLogContext::put);
    }

    private static void put(String key, String value) {
        if (value == null) {
            MDC.remove(key);
        } else {
            MDC.put(key, value);
        }
    }
}
