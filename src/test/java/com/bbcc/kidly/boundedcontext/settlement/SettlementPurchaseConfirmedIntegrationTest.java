package com.bbcc.kidly.boundedcontext.settlement;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementItem;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementItemStatus;
import com.bbcc.kidly.boundedcontext.settlement.out.JpaSettlementItemRepository;
import com.bbcc.kidly.global.eventpublisher.EventPublisher;
import com.bbcc.kidly.shared.order.event.OrderItemPurchaseConfirmedEvent;
import com.bbcc.kidly.support.IntegrationTest;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 구매확정을 받아 정산항목을 저장하는 흐름을 확인한다.
 * 리스너는 발행한 트랜잭션이 커밋된 뒤에 실행되므로 테스트에 @Transactional을 붙이지 않고 발행만 트랜잭션 안에서 한다.
 */
@IntegrationTest
class SettlementPurchaseConfirmedIntegrationTest {

    private static final long ORDER_ITEM_ID = 101L;
    private static final long ORDER_ID = 1L;
    private static final long SELLER_ID = 10L;
    private static final long AMOUNT = 50_000L;
    private static final LocalDateTime CONFIRMED_AT = LocalDateTime.of(2026, 10, 1, 10, 0);

    @Autowired
    private EventPublisher eventPublisher;

    @Autowired
    private TransactionTemplate transactionTemplate;

    // 저장된 정산항목 전체를 읽으려고 JpaRepository의 기본 조회를 쓴다
    @Autowired
    private JpaSettlementItemRepository settlementItemRepository;

    @Test
    @DisplayName("구매확정을 발행한 트랜잭션이 커밋되면 이벤트 값으로 대기 상태의 정산항목이 저장된다")
    void saveItemAfterCommit() {
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(purchaseConfirmedEvent()));

        assertThat(settlementItemRepository.findAll())
                .singleElement()
                .extracting(
                        SettlementItem::getOrderId,
                        SettlementItem::getOrderItemId,
                        SettlementItem::getSellerId,
                        SettlementItem::getSalesAmount,
                        SettlementItem::getConfirmedAt,
                        SettlementItem::getEligibleAt,
                        SettlementItem::getFeeAmount,
                        SettlementItem::getStatus)
                .containsExactly(ORDER_ID, ORDER_ITEM_ID, SELLER_ID, AMOUNT, CONFIRMED_AT,
                        CONFIRMED_AT.plusDays(14), null, SettlementItemStatus.PENDING);
    }

    @Test
    @DisplayName("구매확정을 발행한 트랜잭션이 롤백되면 정산항목이 생기지 않는다")
    void noItemAfterRollback() {
        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publish(purchaseConfirmedEvent());
            status.setRollbackOnly();
        });

        assertThat(settlementItemRepository.findAll()).isEmpty();
    }

    private OrderItemPurchaseConfirmedEvent purchaseConfirmedEvent() {
        return new OrderItemPurchaseConfirmedEvent(UUID.randomUUID(), CONFIRMED_AT,
                new OrderItemPurchaseConfirmedEvent.Data(ORDER_ITEM_ID, ORDER_ID, 2L, SELLER_ID, 3L, 4L, 5L,
                        "120", 2, AMOUNT, CONFIRMED_AT));
    }
}
