package com.bbcc.kidly.boundedcontext.settlement;

import com.bbcc.kidly.global.eventpublisher.EventPublisher;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
final class PayoutRequests {

    private PayoutRequests() {
    }

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

    static void complete(EventPublisher eventPublisher, TransactionTemplate transactionTemplate, Request request) {
        var event = SettlementFixture.payoutCompleted(request.settlementId(), request.sellerId(), request.amount());
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(event));
    }

    record Request(Long settlementId, Long sellerId, long amount) {
    }
}
