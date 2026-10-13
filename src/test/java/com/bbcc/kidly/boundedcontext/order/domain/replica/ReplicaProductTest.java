package com.bbcc.kidly.boundedcontext.order.domain.replica;

import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.BUYER_ID;
import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.OCCURRED_AT;
import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.SELLER_ID;
import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.onSaleProduct;
import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.product;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReplicaProductTest {

    @Test
    @DisplayName("이벤트 값으로 복사본을 만든다")
    void create() {
        ReplicaProduct replica = ReplicaProduct.create(onSaleProduct(25_000L, OCCURRED_AT));

        assertThat(replica.getOptionId()).isEqualTo(1L);
        assertThat(replica.getProductId()).isEqualTo(1L);
        assertThat(replica.getSellerId()).isEqualTo(SELLER_ID);
        assertThat(replica.getProductName()).isEqualTo("상품 A");
        assertThat(replica.getSize()).isEqualTo("110");
        assertThat(replica.unitPrice()).isEqualTo(25_000L);
        assertThat(replica.getCategoryId()).isEqualTo(1L);
        assertThat(replica.getProductStatus()).isEqualTo("ON_SALE");
        assertThat(replica.isSoldOut()).isFalse();
        assertThat(replica.getEventOccurredAt()).isEqualTo(OCCURRED_AT);
    }

    @Test
    @DisplayName("저장된 값보다 늦게 발생한 이벤트면 덮어쓴다")
    void appliesNewerEvent() {
        ReplicaProduct replica = ReplicaProduct.create(onSaleProduct(25_000L, OCCURRED_AT));

        boolean applied = replica.applyIfNewer(onSaleProduct(23_000L, OCCURRED_AT.plusMinutes(1)));

        assertThat(applied).isTrue();
        assertThat(replica.unitPrice()).isEqualTo(23_000L);
        assertThat(replica.getEventOccurredAt()).isEqualTo(OCCURRED_AT.plusMinutes(1));
    }

    @Test
    @DisplayName("같거나 이른 시각의 이벤트는 무시한다 (순서 뒤바뀜 · 중복)")
    void ignoresSameOrOlderEvent() {
        ReplicaProduct replica = ReplicaProduct.create(onSaleProduct(25_000L, OCCURRED_AT));

        assertThat(replica.applyIfNewer(onSaleProduct(23_000L, OCCURRED_AT))).isFalse();
        assertThat(replica.applyIfNewer(onSaleProduct(21_000L, OCCURRED_AT.minusMinutes(1)))).isFalse();
        assertThat(replica.unitPrice()).isEqualTo(25_000L);
    }

    @Test
    @DisplayName("판매 중이고 품절이 아니어야 구매 가능하다. 모르는 판매 상태는 구매 불가")
    void purchasable() {
        assertThat(ReplicaProduct.create(onSaleProduct(25_000L, OCCURRED_AT)).isPurchasable()).isTrue();
        assertThat(ReplicaProduct.create(product(1L, 25_000L, "ON_SALE", true, OCCURRED_AT)).isPurchasable())
            .isFalse();
        assertThat(ReplicaProduct.create(product(1L, 25_000L, "STOPPED", false, OCCURRED_AT)).isPurchasable())
            .isFalse();
        assertThat(ReplicaProduct.create(product(1L, 25_000L, "UNKNOWN", false, OCCURRED_AT)).isPurchasable())
            .isFalse();
    }

    @Test
    @DisplayName("판매자 본인인지 판단한다")
    void soldBy() {
        ReplicaProduct replica = ReplicaProduct.create(onSaleProduct(25_000L, OCCURRED_AT));

        assertThat(replica.isSoldBy(SELLER_ID)).isTrue();
        assertThat(replica.isSoldBy(BUYER_ID)).isFalse();
    }

    @Test
    @DisplayName("이벤트에서 사라진 옵션은 늦게 발생한 이벤트일 때만 품절로 바꾼다")
    void markSoldOutIfNewer() {
        ReplicaProduct replica = ReplicaProduct.create(onSaleProduct(25_000L, OCCURRED_AT));

        assertThat(replica.markSoldOutIfNewer(OCCURRED_AT)).isFalse();
        assertThat(replica.isSoldOut()).isFalse();

        assertThat(replica.markSoldOutIfNewer(OCCURRED_AT.plusMinutes(1))).isTrue();
        assertThat(replica.isSoldOut()).isTrue();
        assertThat(replica.isPurchasable()).isFalse();
        assertThat(replica.getEventOccurredAt()).isEqualTo(OCCURRED_AT.plusMinutes(1));
    }
}
