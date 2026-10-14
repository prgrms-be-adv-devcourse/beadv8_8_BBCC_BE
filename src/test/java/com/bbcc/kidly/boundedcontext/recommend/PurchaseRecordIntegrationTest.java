package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.CHILD_1_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.MEMBER_1_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.ORDER_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.ORDER_ITEM_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_120_OPTION_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PURCHASE_CONFIRMED_AT;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.SELLER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.recommend.app.purchase.PurchaseRecordService;
import com.bbcc.kidly.boundedcontext.recommend.domain.purchase.PurchaseRecord;
import com.bbcc.kidly.boundedcontext.recommend.out.purchase.JpaPurchaseRecordRepository;
import com.bbcc.kidly.global.eventpublisher.EventPublisher;
import com.bbcc.kidly.shared.order.event.OrderItemPurchaseConfirmedEvent;
import com.bbcc.kidly.support.IntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 구매확정을 받아 구매 기록을 저장하는 흐름을 확인한다.
 * 리스너는 발행한 트랜잭션이 커밋된 뒤에 실행되므로 테스트에 @Transactional을 붙이지 않고 발행만 트랜잭션 안에서 한다.
 */
@IntegrationTest
class PurchaseRecordIntegrationTest {

    @Autowired
    private EventPublisher eventPublisher;

    @Autowired
    private TransactionTemplate transactionTemplate;

    // 저장된 기록 전체를 읽으려고 JpaRepository의 기본 조회를 쓴다
    @Autowired
    private JpaPurchaseRecordRepository purchaseRecordRepository;

    // 리스너의 예외는 커밋 뒤라 로그로만 남으므로, 저장 규칙은 서비스를 직접 불러 예외까지 확인한다
    @Autowired
    private PurchaseRecordService purchaseRecordService;

    @Test
    @DisplayName("구매확정을 발행한 트랜잭션이 커밋되면 이벤트 값이 구매 기록에 저장된다")
    void saveRecordAfterCommit() {
        publishInTransaction(RecommendFixture.purchaseConfirmedEvent());

        assertThat(purchaseRecordRepository.findAll())
            .singleElement()
            .extracting(
                PurchaseRecord::getOrderItemId,
                PurchaseRecord::getOrderId,
                PurchaseRecord::getMemberId,
                PurchaseRecord::getChildProfileId,
                PurchaseRecord::getProductId,
                PurchaseRecord::getProductOptionId,
                PurchaseRecord::getSize,
                PurchaseRecord::getConfirmedAt)
            .containsExactly(ORDER_ITEM_ID, ORDER_ID, MEMBER_1_ID, CHILD_1_ID, PRODUCT_A_ID, PRODUCT_A_120_OPTION_ID,
                "120", PURCHASE_CONFIRMED_AT);
    }

    @Test
    @DisplayName("구매확정을 발행한 트랜잭션이 롤백되면 구매 기록이 생기지 않는다")
    void noRecordAfterRollback() {
        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publish(RecommendFixture.purchaseConfirmedEvent());
            status.setRollbackOnly();
        });

        assertThat(purchaseRecordRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("구매 기록 저장이 실패해도 구매확정을 발행한 트랜잭션은 성공하고 기록은 남지 않는다")
    void noRecordWhenListenerFails() {
        OrderItemPurchaseConfirmedEvent withoutSize = new OrderItemPurchaseConfirmedEvent(UUID.randomUUID(),
            PURCHASE_CONFIRMED_AT, new OrderItemPurchaseConfirmedEvent.Data(ORDER_ITEM_ID, ORDER_ID, MEMBER_1_ID,
                SELLER_ID, CHILD_1_ID, PRODUCT_A_ID, PRODUCT_A_120_OPTION_ID, null, 2, 50_000L,
                PURCHASE_CONFIRMED_AT));

        assertThatCode(() -> publishInTransaction(withoutSize)).doesNotThrowAnyException();

        assertThat(purchaseRecordRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("INV-R03 자녀를 고르지 않은 구매확정은 예외 없이 구매 기록으로 저장하지 않는다")
    void skipConfirmationWithoutChild() {
        assertThatCode(() -> purchaseRecordService.recordPurchaseConfirmed(
            RecommendFixture.purchaseConfirmedEventFor(null)))
            .doesNotThrowAnyException();

        assertThat(purchaseRecordRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("INV-R03 같은 구매확정을 다시 받아도 예외 없이 구매 기록은 한 줄이다")
    void keepOneRecordForSameConfirmation() {
        purchaseRecordService.recordPurchaseConfirmed(RecommendFixture.purchaseConfirmedEvent());

        assertThatCode(() -> purchaseRecordService.recordPurchaseConfirmed(RecommendFixture.purchaseConfirmedEvent()))
            .doesNotThrowAnyException();

        assertThat(purchaseRecordRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("INV-R03 같은 주문상품의 구매 기록을 두 줄 저장하면 UNIQUE 제약이 막는다")
    void rejectSecondRecordOfSameOrderItem() {
        purchaseRecordRepository.saveAndFlush(PurchaseRecord.create(RecommendFixture.purchaseConfirmation()));

        assertThatThrownBy(
            () -> purchaseRecordRepository.saveAndFlush(PurchaseRecord.create(RecommendFixture.purchaseConfirmation())))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(purchaseRecordRepository.findAll()).hasSize(1);
    }

    private void publishInTransaction(OrderItemPurchaseConfirmedEvent event) {
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(event));
    }
}
