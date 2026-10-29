package com.bbcc.kidly.boundedcontext.order;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 주문 테스트가 저장된 주문 값을 DB에서 다시 읽는 곳(재전달 · 늦은 도착 테스트의 버전 · 상태 이력).
 * 테스트 클래스패스의 컴포넌트 스캔에 잡혀 모든 통합 · 기능 테스트 컨텍스트에 똑같이 등록된다(OrderFlowEventRecorder와 같다).
 */
@Component
@RequiredArgsConstructor
public class OrderRows {

    /**
     * 주문하기 줄(구매자가 주문해 결제대기가 됨). 상태 이력의 첫 줄이다.
     */
    public static final String PLACED = "ORDER null>PENDING_PAYMENT POST /api/v1/orders";

    private final JdbcTemplate jdbcTemplate;

    public long versionOf(Long orderId) {
        return jdbcTemplate.queryForObject("select version from orders.orders where id = ?", Long.class, orderId);
    }

    /**
     * 주문의 상태 이력 전부(주문하기 줄 · 주문상품 · 배송 포함)를 기록 순서대로 "대상 이전>다음 사유 출처"로.
     * 이전 상태가 없으면 null, 사유가 없으면 사유 칸을 뺀다.
     */
    public List<String> historyRows(Long orderId) {
        return jdbcTemplate.query("select target_type, from_status, to_status, reason, source "
                + "from orders.order_status_history where order_id = ? order by id",
            (row, index) -> row.getString("target_type") + " " + row.getString("from_status") + ">"
                + row.getString("to_status") + (row.getString("reason") == null ? "" : " " + row.getString("reason"))
                + " " + row.getString("source"),
            orderId);
    }
}
