package com.bbcc.kidly.boundedcontext.settlement;

import static com.bbcc.kidly.boundedcontext.settlement.SettlementFixture.CONFIRMED_AT;
import static com.bbcc.kidly.boundedcontext.settlement.SettlementFixture.SELLER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementItem;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementItemCreateInfo;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementItemStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SettlementItemTest {

    @Test
    @DisplayName("구매확정 값으로 대기 상태의 정산 항목을 만든다")
    void create() {
        var item = SettlementFixture.item(101L);

        assertThat(item.getStatus()).isEqualTo(SettlementItemStatus.PENDING);
        assertThat(item.getOrderId()).isEqualTo(1L);
        assertThat(item.getOrderItemId()).isEqualTo(101L);
        assertThat(item.getSellerId()).isEqualTo(SELLER_ID);
        assertThat(item.getSalesAmount()).isEqualTo(10_000L);
        assertThat(item.getConfirmedAt()).isEqualTo(CONFIRMED_AT);
        assertThat(item.getSettlementId()).isNull();
    }

    @Test
    @DisplayName("판매금액이 0원이어도 만든다")
    void createZeroAmount() {
        var item = SettlementItem.create(new SettlementItemCreateInfo(1L, 101L, SELLER_ID, 0L, CONFIRMED_AT));

        assertThat(item.getSalesAmount()).isZero();
    }

    @Test
    @DisplayName("필수 값이 없으면 만들지 않는다")
    void createRejectsNull() {
        assertThatThrownBy(() -> SettlementItem.create(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> SettlementItem.create(
            new SettlementItemCreateInfo(null, 101L, SELLER_ID, 1L, CONFIRMED_AT)))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> SettlementItem.create(
            new SettlementItemCreateInfo(1L, null, SELLER_ID, 1L, CONFIRMED_AT)))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> SettlementItem.create(
            new SettlementItemCreateInfo(1L, 101L, null, 1L, CONFIRMED_AT)))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> SettlementItem.create(
            new SettlementItemCreateInfo(1L, 101L, SELLER_ID, null, CONFIRMED_AT)))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> SettlementItem.create(
            new SettlementItemCreateInfo(1L, 101L, SELLER_ID, 1L, null)))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("ID가 0 이하이거나 판매금액이 음수면 만들지 않는다")
    void createRejectsInvalidValue() {
        assertThatThrownBy(() -> SettlementItem.create(
            new SettlementItemCreateInfo(0L, 101L, SELLER_ID, 1L, CONFIRMED_AT)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SettlementItem.create(
            new SettlementItemCreateInfo(1L, 0L, SELLER_ID, 1L, CONFIRMED_AT)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SettlementItem.create(
            new SettlementItemCreateInfo(1L, 101L, 0L, 1L, CONFIRMED_AT)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SettlementItem.create(
            new SettlementItemCreateInfo(1L, 101L, SELLER_ID, -1L, CONFIRMED_AT)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("대기 항목을 정산하면 정산서 ID를 갖고 SETTLED가 된다")
    void settle() {
        var item = SettlementFixture.item(101L);

        item.settle(7L);

        assertThat(item.getStatus()).isEqualTo(SettlementItemStatus.SETTLED);
        assertThat(item.getSettlementId()).isEqualTo(7L);
    }

    @Test
    @DisplayName("이미 정산했거나 제외한 항목은 다시 정산하지 않는다")
    void settleRejectsNotPending() {
        var settled = SettlementFixture.item(101L);
        settled.settle(7L);
        var excluded = SettlementFixture.item(102L);
        excluded.exclude();

        assertThatThrownBy(() -> settled.settle(8L)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> excluded.settle(8L)).isInstanceOf(IllegalStateException.class);
        assertThat(settled.getSettlementId()).isEqualTo(7L);
    }

    @Test
    @DisplayName("정산서 ID 없이 정산하지 않는다")
    void settleRejectsNull() {
        assertThatThrownBy(() -> SettlementFixture.item(101L).settle(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("대기 항목을 제외하고, 다시 제외해도 그대로다")
    void exclude() {
        var item = SettlementFixture.item(101L);

        item.exclude();
        item.exclude();

        assertThat(item.getStatus()).isEqualTo(SettlementItemStatus.EXCLUDED);
    }

    @Test
    @DisplayName("정산된 항목은 제외하지 않는다")
    void excludeRejectsSettled() {
        var item = SettlementFixture.item(101L);
        item.settle(7L);

        assertThatThrownBy(item::exclude).isInstanceOf(IllegalStateException.class);
        assertThat(item.getStatus()).isEqualTo(SettlementItemStatus.SETTLED);
    }

    @Test
    @DisplayName("0 이하의 정산서 ID로는 정산하지 않는다")
    void settleRejectsNonPositiveSettlementId() {
        var item = SettlementFixture.item(101L);

        assertThatThrownBy(() -> item.settle(0L)).isInstanceOf(IllegalArgumentException.class);
        assertThat(item.getStatus()).isEqualTo(SettlementItemStatus.PENDING);
    }
}
