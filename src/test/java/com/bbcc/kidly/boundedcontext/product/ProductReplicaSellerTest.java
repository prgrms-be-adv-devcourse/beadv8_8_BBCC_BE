package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProductReplicaSellerFixture.BUSINESS_NAME;
import static com.bbcc.kidly.boundedcontext.product.ProductReplicaSellerFixture.SELLER_CHANGED_AT;
import static com.bbcc.kidly.boundedcontext.product.ProductReplicaSellerFixture.SELLER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.product.domain.replica.ProductReplicaSeller;
import com.bbcc.kidly.shared.seller.domain.SellerStatus;
import java.time.LocalDateTime;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class ProductReplicaSellerTest {

    @Test
    @DisplayName("처음 받은 판매자 정보로 복사본을 만든다")
    void createWithFirstChange() {
        ProductReplicaSeller replica = ProductReplicaSeller.create(ProductReplicaSellerFixture.sellerApproved());

        assertThat(replica)
            .extracting(
                ProductReplicaSeller::getSellerId,
                ProductReplicaSeller::getBusinessName,
                ProductReplicaSeller::getStatus,
                ProductReplicaSeller::getEventOccurredAt)
            .containsExactly(SELLER_ID, "테스트상점", SellerStatus.ACTIVE, SELLER_CHANGED_AT);
    }

    @Test
    @DisplayName("더 늦게 일어난 이벤트의 상호명과 상태로 덮어쓴다")
    void applyLaterChange() {
        ProductReplicaSeller replica = ProductReplicaSeller.create(ProductReplicaSellerFixture.sellerApproved());
        LocalDateTime later = SELLER_CHANGED_AT.plusMinutes(1);

        replica.applyIfNewer(
            ProductReplicaSellerFixture.sellerChangeAt("새상점", SellerStatus.SUSPENDED, later));

        assertThat(replica)
            .extracting(
                ProductReplicaSeller::getSellerId,
                ProductReplicaSeller::getBusinessName,
                ProductReplicaSeller::getStatus,
                ProductReplicaSeller::getEventOccurredAt)
            .containsExactly(SELLER_ID, "새상점", SellerStatus.SUSPENDED, later);
    }

    @ParameterizedTest(name = "저장된 시각에서 {0}분")
    @ValueSource(longs = {0, -1})
    @DisplayName("같은 시각이거나 더 이르게 일어난 이벤트는 무시한다")
    void ignoreChangeNotLater(long minutesFromStored) {
        ProductReplicaSeller replica = ProductReplicaSeller.create(ProductReplicaSellerFixture.sellerApproved());

        replica.applyIfNewer(ProductReplicaSellerFixture.sellerChangeAt(
            "새상점", SellerStatus.SUSPENDED, SELLER_CHANGED_AT.plusMinutes(minutesFromStored)));

        assertThat(replica)
            .extracting(
                ProductReplicaSeller::getBusinessName,
                ProductReplicaSeller::getStatus,
                ProductReplicaSeller::getEventOccurredAt)
            .containsExactly("테스트상점", SellerStatus.ACTIVE, SELLER_CHANGED_AT);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("firstChangesWithoutRequiredValue")
    @DisplayName("필수 값이 없는 판매자 정보로는 복사본을 만들지 않는다")
    void rejectCreateWithoutRequiredValue(ProductReplicaSeller.ChangeInfo info) {
        assertThatThrownBy(() -> ProductReplicaSeller.create(info))
            .isInstanceOf(NullPointerException.class);
    }

    static Stream<Named<ProductReplicaSeller.ChangeInfo>> firstChangesWithoutRequiredValue() {
        return Stream.of(
            Named.of("판매자 정보 없음", null),
            Named.of("판매자 ID 없음",
                new ProductReplicaSeller.ChangeInfo(null, BUSINESS_NAME, SellerStatus.ACTIVE, SELLER_CHANGED_AT)),
            Named.of("상호명 없음",
                ProductReplicaSellerFixture.sellerChangeAt(null, SellerStatus.ACTIVE, SELLER_CHANGED_AT)),
            Named.of("판매자 상태 없음",
                ProductReplicaSellerFixture.sellerChangeAt(BUSINESS_NAME, null, SELLER_CHANGED_AT)),
            Named.of("발생 시각 없음",
                ProductReplicaSellerFixture.sellerChangeAt(BUSINESS_NAME, SellerStatus.ACTIVE, null)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("laterChangesWithoutRequiredValue")
    @DisplayName("덮어쓸 판매자 정보에 필수 값이 없으면 예외이고 복사본은 그대로다")
    void rejectApplyWithoutRequiredValue(ProductReplicaSeller.ChangeInfo info) {
        ProductReplicaSeller replica = ProductReplicaSeller.create(ProductReplicaSellerFixture.sellerApproved());

        assertThatThrownBy(() -> replica.applyIfNewer(info))
            .isInstanceOf(NullPointerException.class);

        assertThat(replica)
            .extracting(
                ProductReplicaSeller::getBusinessName,
                ProductReplicaSeller::getStatus,
                ProductReplicaSeller::getEventOccurredAt)
            .containsExactly("테스트상점", SellerStatus.ACTIVE, SELLER_CHANGED_AT);
    }

    static Stream<Named<ProductReplicaSeller.ChangeInfo>> laterChangesWithoutRequiredValue() {
        LocalDateTime later = SELLER_CHANGED_AT.plusMinutes(1);
        return Stream.of(
            Named.of("판매자 정보 없음", null),
            Named.of("상호명 없음", ProductReplicaSellerFixture.sellerChangeAt(null, SellerStatus.SUSPENDED, later)),
            Named.of("판매자 상태 없음", ProductReplicaSellerFixture.sellerChangeAt("새상점", null, later)),
            Named.of("발생 시각 없음", ProductReplicaSellerFixture.sellerChangeAt("새상점", SellerStatus.SUSPENDED, null)));
    }
}
