package com.bbcc.kidly.boundedcontext.settlement;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.settlement.app.SettlementRunService;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementItem;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementItemStatus;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementRepository;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementStatus;
import com.bbcc.kidly.boundedcontext.settlement.out.JpaSettlementItemRepository;
import com.bbcc.kidly.global.eventpublisher.EventPublisher;
import com.bbcc.kidly.shared.event.DomainEvent;
import com.bbcc.kidly.shared.order.event.OrderItemPurchaseConfirmedEvent;
import com.bbcc.kidly.shared.payment.event.PaymentPayoutCompletedEvent;
import com.bbcc.kidly.support.IntegrationTest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 구매확정부터 지급 요청 · 지급 완료까지 실제 리스너로 이어지는 정산 흐름을 한 번에 확인한다.
 * 입금은 결제 서비스가 한다(#525). 테스트가 결제 서비스 대신 지급 완료 결과를 발행한다(PayoutRequests).
 * 리스너는 발행한 트랜잭션이 커밋된 뒤 같은 스레드에서 실행되므로 각 단계가 끝난 뒤 바로 검증할 수 있다.
 */
@IntegrationTest
class SettlementFlowIntegrationTest {

    private static final Long ADMIN_ID = 3L;
    private static final Long SELLER_ID = 10L;
    // 수동 실행은 미래 기준일을 거부하므로 기준일은 이번 달 1일(오늘 또는 지난 날)로 잡는다
    private static final LocalDate CUTOFF_ON = LocalDate.now().withDayOfMonth(1);
    private static final LocalDateTime CONFIRMED_AT = CUTOFF_ON.atStartOfDay().minusDays(1);

    @Autowired
    private EventPublisher eventPublisher;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private SettlementRunService settlementRunService;

    // 저장된 정산 항목 전체를 읽으려고 JpaRepository의 기본 조회를 쓴다
    @Autowired
    private JpaSettlementItemRepository settlementItemRepository;

    @Autowired
    private SettlementRepository settlementRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("구매확정 → 정산 대상 → 수동 실행 → 정산금액으로 지급 요청 → 지급 완료 결과를 받으면 정산서는 지급 완료다")
    void purchaseConfirmedToPaid() {
        publishPurchaseConfirmed(101L, 30_000L);
        publishPurchaseConfirmed(102L, 15_009L);
        assertThat(settlementItemRepository.findAll())
            .extracting(SettlementItem::getStatus)
            .containsExactly(SettlementItemStatus.PENDING, SettlementItemStatus.PENDING);

        var result = settlementRunService.runManually(CUTOFF_ON, ADMIN_ID);

        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.successCount()).isEqualTo(1);
        var settlementId = jdbcTemplate.queryForObject("SELECT id FROM settlement.settlement", Long.class);
        var settlement = settlementRepository.findById(settlementId).orElseThrow();
        assertThat(settlement.getTotalSalesAmount()).isEqualTo(45_009L);
        assertThat(settlement.getFeeAmount()).isEqualTo(4_500L);
        assertThat(settlement.getSettlementAmount()).isEqualTo(40_509L);
        assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.COMPLETED);
        var requests = PayoutRequests.all(jdbcTemplate);
        assertThat(requests).containsExactly(new PayoutRequests.Request(settlementId, SELLER_ID, 40_509L));

        PayoutRequests.complete(eventPublisher, transactionTemplate, requests.getFirst());

        settlement = settlementRepository.findById(settlementId).orElseThrow();
        assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.PAID);
        assertThat(settlement.getPaidAt()).isNotNull();
        assertThat(settlementItemRepository.findAll()).allSatisfy(item -> {
            assertThat(item.getStatus()).isEqualTo(SettlementItemStatus.SETTLED);
            assertThat(item.getSettlementId()).isEqualTo(settlementId);
        });
    }

    private void publishPurchaseConfirmed(Long orderItemId, long amount) {
        var event = new OrderItemPurchaseConfirmedEvent(UUID.randomUUID(), CONFIRMED_AT,
            new OrderItemPurchaseConfirmedEvent.Data(orderItemId, 1L, 2L, SELLER_ID, 3L, 4L, 5L,
                "120", 1, amount, CONFIRMED_AT));
        publishInTransaction(event);
    }

    private void publishInTransaction(DomainEvent event) {
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(event));
    }
}
