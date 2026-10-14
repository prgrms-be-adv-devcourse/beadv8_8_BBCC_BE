package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.CHILD_1_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.MEMBER_1_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.ORDER_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.ORDER_ITEM_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_120_OPTION_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PURCHASE_CONFIRMED_AT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.recommend.domain.purchase.PurchaseRecord;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

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

    @Test
    @DisplayName("기록 대상을 판단할 구매확정 값이 없으면 거절한다")
    void rejectRecordableCheckWithNull() {
        assertThatThrownBy(() -> PurchaseRecord.isRecordable(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("필수입니다");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("creationsWithNull")
    @DisplayName("구매확정 값에 필수 값이 없으면 기록을 만들지 않는다")
    void rejectCreationWithNull(ThrowingCallable creation) {
        assertThatThrownBy(creation)
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("필수입니다");
    }

    static Stream<Named<ThrowingCallable>> creationsWithNull() {
        return Stream.of(
            Named.of("구매확정 값", () -> PurchaseRecord.create(null)),
            Named.of("주문상품 ID", () -> PurchaseRecord.create(new PurchaseRecord.RecordInfo(
                null, ORDER_ID, MEMBER_1_ID, CHILD_1_ID, PRODUCT_A_ID, PRODUCT_A_120_OPTION_ID, "120",
                PURCHASE_CONFIRMED_AT))),
            Named.of("주문 ID", () -> PurchaseRecord.create(new PurchaseRecord.RecordInfo(
                ORDER_ITEM_ID, null, MEMBER_1_ID, CHILD_1_ID, PRODUCT_A_ID, PRODUCT_A_120_OPTION_ID, "120",
                PURCHASE_CONFIRMED_AT))),
            Named.of("회원 ID", () -> PurchaseRecord.create(new PurchaseRecord.RecordInfo(
                ORDER_ITEM_ID, ORDER_ID, null, CHILD_1_ID, PRODUCT_A_ID, PRODUCT_A_120_OPTION_ID, "120",
                PURCHASE_CONFIRMED_AT))),
            Named.of("자녀 ID", () -> PurchaseRecord.create(new PurchaseRecord.RecordInfo(
                ORDER_ITEM_ID, ORDER_ID, MEMBER_1_ID, null, PRODUCT_A_ID, PRODUCT_A_120_OPTION_ID, "120",
                PURCHASE_CONFIRMED_AT))),
            Named.of("상품 ID", () -> PurchaseRecord.create(new PurchaseRecord.RecordInfo(
                ORDER_ITEM_ID, ORDER_ID, MEMBER_1_ID, CHILD_1_ID, null, PRODUCT_A_120_OPTION_ID, "120",
                PURCHASE_CONFIRMED_AT))),
            Named.of("옵션 ID", () -> PurchaseRecord.create(new PurchaseRecord.RecordInfo(
                ORDER_ITEM_ID, ORDER_ID, MEMBER_1_ID, CHILD_1_ID, PRODUCT_A_ID, null, "120",
                PURCHASE_CONFIRMED_AT))),
            Named.of("사이즈", () -> PurchaseRecord.create(new PurchaseRecord.RecordInfo(
                ORDER_ITEM_ID, ORDER_ID, MEMBER_1_ID, CHILD_1_ID, PRODUCT_A_ID, PRODUCT_A_120_OPTION_ID, null,
                PURCHASE_CONFIRMED_AT))),
            Named.of("구매확정 시각", () -> PurchaseRecord.create(new PurchaseRecord.RecordInfo(
                ORDER_ITEM_ID, ORDER_ID, MEMBER_1_ID, CHILD_1_ID, PRODUCT_A_ID, PRODUCT_A_120_OPTION_ID, "120",
                null))));
    }
}
