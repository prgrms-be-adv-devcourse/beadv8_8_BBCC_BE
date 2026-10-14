package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.CHILD_1_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.MEMBER_1_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.ORDER_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.ORDER_ITEM_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_120_OPTION_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PURCHASE_CONFIRMED_AT;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.domain.purchase.PurchaseRecord;
import com.bbcc.kidly.boundedcontext.recommend.out.purchase.JpaPurchaseRecordRepository;
import com.bbcc.kidly.global.eventpublisher.EventPublisher;
import com.bbcc.kidly.shared.order.event.OrderItemPurchaseConfirmedEvent;
import com.bbcc.kidly.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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

    private void publishInTransaction(OrderItemPurchaseConfirmedEvent event) {
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(event));
    }
}
