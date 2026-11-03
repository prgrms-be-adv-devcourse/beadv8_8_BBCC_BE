package com.bbcc.kidly.boundedcontext.settlement;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.settlement.app.SettlementRunService;
import com.bbcc.kidly.boundedcontext.settlement.domain.Settlement;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementItemRepository;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementRepository;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementStatus;
import com.bbcc.kidly.global.eventpublisher.EventPublisher;
import com.bbcc.kidly.shared.event.DomainEvent;
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

// 정산이 지급을 요청하고, 지급 결과가 정산서에 반영되는 흐름을 검증한다. 입금은 결제 서비스가 하므로(#525) 결과는 테스트가 발행한다(PayoutRequests)
@IntegrationTest
class SettlementPayoutIntegrationTest {

    private static final Long ADMIN_ID = 3L;
    private static final Long SELLER_ID = 10L;
    private static final LocalDate CUTOFF_ON = LocalDate.now().withDayOfMonth(1);
    private static final LocalDateTime CONFIRMED_AT = CUTOFF_ON.atStartOfDay().minusDays(1);

    @Autowired
    private SettlementRunService settlementRunService;

    @Autowired
    private SettlementItemRepository settlementItemRepository;

    @Autowired
    private SettlementRepository settlementRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EventPublisher eventPublisher;


    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    @DisplayName("정산서마다 정산금액으로 지급을 요청하고, 지급 완료 결과를 받으면 정산서가 지급 완료가 된다")
    void paid() {
        saveItem(1L, 10_000L);

        settlementRunService.runManually(CUTOFF_ON, ADMIN_ID);

        var requested = onlySettlement();
        assertThat(requested.getStatus()).isEqualTo(SettlementStatus.COMPLETED);
        var requests = PayoutRequests.all(jdbcTemplate);
        assertThat(requests).containsExactly(
            new PayoutRequests.Request(requested.getId(), SELLER_ID, requested.getSettlementAmount()));

        PayoutRequests.complete(eventPublisher, transactionTemplate, requests.getFirst());

        var settlement = onlySettlement();
        assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.PAID);
        assertThat(settlement.getPaidAt()).isNotNull();
        assertThat(settlement.getFailReason()).isNull();
    }

    @Test
    @DisplayName("0원 정산도 지급을 요청하고 지급 완료가 된다")
    void zeroAmount() {
        saveItem(1L, 0L);

        settlementRunService.runManually(CUTOFF_ON, ADMIN_ID);

        var requests = PayoutRequests.all(jdbcTemplate);
        assertThat(requests).singleElement().extracting(PayoutRequests.Request::amount).isEqualTo(0L);
        PayoutRequests.complete(eventPublisher, transactionTemplate, requests.getFirst());
        assertThat(onlySettlement().getStatus()).isEqualTo(SettlementStatus.PAID);
    }

    @Test
    @DisplayName("같은 지급 완료 결과를 두 번 받아도 한 번만 반영한다")
    void duplicateCompleted() {
        var settlementId = saveSettlement().getId();
        var first = LocalDateTime.of(2026, 11, 1, 9, 0);

        publishInTransaction(completed(settlementId, SELLER_ID, 9_000L, first));
        publishInTransaction(completed(settlementId, SELLER_ID, 9_000L, first.plusMinutes(5)));

        var settlement = settlementRepository.findById(settlementId).orElseThrow();
        assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.PAID);
        assertThat(settlement.getPaidAt()).isEqualTo(first);
    }

    @Test
    @DisplayName("판매자나 금액이 다른 지급 완료 결과는 반영하지 않는다")
    void mismatchedCompleted() {
        var settlementId = saveSettlement().getId();
        var paidAt = LocalDateTime.of(2026, 11, 1, 9, 0);

        publishInTransaction(completed(settlementId, SELLER_ID + 1, 9_000L, paidAt));
        publishInTransaction(completed(settlementId, SELLER_ID, 9_001L, paidAt));

        var settlement = settlementRepository.findById(settlementId).orElseThrow();
        assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.COMPLETED);
        assertThat(settlement.getPaidAt()).isNull();
    }

    private Settlement saveSettlement() {
        return settlementRepository.save(SettlementFixture.settlement(10_000L));
    }

    private Settlement onlySettlement() {
        var id = jdbcTemplate.queryForObject("SELECT id FROM settlement.settlement", Long.class);
        return settlementRepository.findById(id).orElseThrow();
    }

    private void saveItem(Long orderItemId, long salesAmount) {
        settlementItemRepository.save(SettlementFixture.item(orderItemId, SELLER_ID, salesAmount, CONFIRMED_AT));
    }

    private void publishInTransaction(DomainEvent event) {
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(event));
    }

    private static PaymentPayoutCompletedEvent completed(
        Long settlementId, Long sellerId, long amount, LocalDateTime paidAt) {
        return new PaymentPayoutCompletedEvent(UUID.randomUUID(), LocalDateTime.now(),
            new PaymentPayoutCompletedEvent.Data(settlementId, sellerId, amount, 1L, paidAt));
    }
}
