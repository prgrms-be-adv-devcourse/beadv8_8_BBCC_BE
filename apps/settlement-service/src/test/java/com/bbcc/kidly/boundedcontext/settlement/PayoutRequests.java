package com.bbcc.kidly.boundedcontext.settlement;

import com.bbcc.kidly.global.eventpublisher.EventPublisher;
import com.bbcc.kidly.shared.payment.event.PaymentPayoutCompletedEvent;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 지급 요청을 받아 입금하는 쪽을 테스트에서 흉내 낸다.
 * 정산이 보낸 지급 요청(settlement.payout-requested, 정산 아웃박스)을 읽고, 결제 서비스 대신 지급 완료 결과를 발행한다.
 * 결제 쪽 입금 · 지급 기록은 결제 서비스의 SettlementPayoutIntegrationTest가 본다.
 */
final class PayoutRequests {

    private static final long LEDGER_ID = 1L;

    private PayoutRequests() {
    }

    // 보낸 순서대로
    static List<Request> all(JdbcTemplate jdbc) {
        return jdbc.query("""
            SELECT (payload::jsonb -> 'data' ->> 'settlementId')::bigint AS settlement_id,
                   (payload::jsonb -> 'data' ->> 'sellerId')::bigint AS seller_id,
                   (payload::jsonb -> 'data' ->> 'amount')::bigint AS amount
            FROM settlement.settlement_outbox
            WHERE event_type = 'settlement.payout-requested'
            ORDER BY id
            """, (rs, row) -> new Request(rs.getLong("settlement_id"), rs.getLong("seller_id"), rs.getLong("amount")));
    }

    static List<Request> of(JdbcTemplate jdbc, Long settlementId) {
        return all(jdbc).stream().filter(request -> request.settlementId().equals(settlementId)).toList();
    }

    // 결제 서비스가 입금하고 보내는 결과와 같은 값으로 발행한다
    static void complete(EventPublisher eventPublisher, TransactionTemplate transactionTemplate, Request request) {
        var event = new PaymentPayoutCompletedEvent(UUID.randomUUID(), LocalDateTime.now(),
            new PaymentPayoutCompletedEvent.Data(request.settlementId(), request.sellerId(), request.amount(),
                LEDGER_ID, LocalDateTime.now()));
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(event));
    }

    record Request(Long settlementId, Long sellerId, long amount) {
    }
}
