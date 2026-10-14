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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PurchaseRecordTest {

    @Test
    @DisplayName("구매확정 값으로 구매 기록을 만든다")
    void createFromConfirmation() {
        PurchaseRecord record = PurchaseRecord.create(RecommendFixture.purchaseConfirmation());

        assertThat(record)
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
    @DisplayName("INV-R03 자녀를 고른 구매확정은 기록할 수 있다")
    void recordableWhenChildChosen() {
        assertThat(PurchaseRecord.isRecordable(RecommendFixture.purchaseConfirmation())).isTrue();
    }

    @Test
    @DisplayName("INV-R03 자녀를 고르지 않은 구매확정은 기록하지 않는다")
    void notRecordableWithoutChild() {
        assertThat(PurchaseRecord.isRecordable(RecommendFixture.purchaseConfirmationFor(null))).isFalse();
    }
}
