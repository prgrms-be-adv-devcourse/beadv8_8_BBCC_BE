package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_SELLER_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendReplicaSellerFixture.BUSINESS_NAME;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendReplicaSellerFixture.SELLER_CHANGED_AT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaSeller;
import com.bbcc.kidly.shared.seller.domain.SellerStatus;
import java.time.LocalDateTime;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class RecommendReplicaSellerTest {

    @Test
    @DisplayName("처음 받은 판매자 정보로 복사본을 만든다")
    void createWithFirstChange() {
        RecommendReplicaSeller replica = RecommendReplicaSeller.create(RecommendReplicaSellerFixture.sellerApproved());

        assertThat(replica)
            .extracting(
                RecommendReplicaSeller::getSellerId,
                RecommendReplicaSeller::getBusinessName,
                RecommendReplicaSeller::getStatus,
                RecommendReplicaSeller::getEventOccurredAt)
            .containsExactly(PRODUCT_A_SELLER_ID, "소소한옷장", SellerStatus.ACTIVE, SELLER_CHANGED_AT);
    }

    @Test
    @DisplayName("INV-R01 더 늦게 일어난 이벤트의 상호명과 상태로 덮어쓴다")
    void applyLaterChange() {
        RecommendReplicaSeller replica = RecommendReplicaSeller.create(RecommendReplicaSellerFixture.sellerApproved());
        LocalDateTime later = SELLER_CHANGED_AT.plusMinutes(1);

        replica.applyIfNewer(RecommendReplicaSellerFixture.sellerChangeAt("새옷장", SellerStatus.SUSPENDED, later));

        assertThat(replica)
            .extracting(
                RecommendReplicaSeller::getSellerId,
                RecommendReplicaSeller::getBusinessName,
                RecommendReplicaSeller::getStatus,
                RecommendReplicaSeller::getEventOccurredAt)
            .containsExactly(PRODUCT_A_SELLER_ID, "새옷장", SellerStatus.SUSPENDED, later);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("changesNotLater")
    @DisplayName("INV-R01 같은 시각이거나 더 이르게 일어난 이벤트는 필수 값이 없어도 무시한다")
    void ignoreChangeNotLater(RecommendReplicaSeller.ChangeInfo info) {
        RecommendReplicaSeller replica = RecommendReplicaSeller.create(RecommendReplicaSellerFixture.sellerApproved());

        replica.applyIfNewer(info);

        assertThat(replica)
            .extracting(
                RecommendReplicaSeller::getBusinessName,
                RecommendReplicaSeller::getStatus,
                RecommendReplicaSeller::getEventOccurredAt)
            .containsExactly("소소한옷장", SellerStatus.ACTIVE, SELLER_CHANGED_AT);
    }

    // 이미 반영한 이벤트를 다시 받거나 이전 이벤트가 늦게 도착한 경우다. 값이 비어 있어도 예외 없이 무시해야 한다
    static Stream<Named<RecommendReplicaSeller.ChangeInfo>> changesNotLater() {
        LocalDateTime earlier = SELLER_CHANGED_AT.minusMinutes(1);
        return Stream.of(
            Named.of("같은 시각",
                RecommendReplicaSellerFixture.sellerChangeAt("새옷장", SellerStatus.SUSPENDED, SELLER_CHANGED_AT)),
            Named.of("1분 이른 시각",
                RecommendReplicaSellerFixture.sellerChangeAt("새옷장", SellerStatus.SUSPENDED, earlier)),
            Named.of("같은 시각 · 상호명 없음",
                RecommendReplicaSellerFixture.sellerChangeAt(null, SellerStatus.SUSPENDED, SELLER_CHANGED_AT)),
            Named.of("1분 이른 시각 · 판매자 상태 없음",
                RecommendReplicaSellerFixture.sellerChangeAt("새옷장", null, earlier)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("firstChangesWithNull")
    @DisplayName("판매자 정보에 필수 값이 없으면 복사본을 만들지 않는다")
    void rejectCreationWithNull(RecommendReplicaSeller.ChangeInfo info) {
        assertThatThrownBy(() -> RecommendReplicaSeller.create(info))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("필수입니다");
    }

    static Stream<Named<RecommendReplicaSeller.ChangeInfo>> firstChangesWithNull() {
        return Stream.of(
            Named.of("판매자 정보", null),
            Named.of("판매자 ID",
                new RecommendReplicaSeller.ChangeInfo(null, BUSINESS_NAME, SellerStatus.ACTIVE, SELLER_CHANGED_AT)),
            Named.of("상호명", RecommendReplicaSellerFixture.sellerChangeAt(null, SellerStatus.ACTIVE, SELLER_CHANGED_AT)),
            Named.of("판매자 상태", RecommendReplicaSellerFixture.sellerChangeAt(BUSINESS_NAME, null, SELLER_CHANGED_AT)),
            Named.of("이벤트 발생 시각",
                RecommendReplicaSellerFixture.sellerChangeAt(BUSINESS_NAME, SellerStatus.ACTIVE, null)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("laterChangesWithNull")
    @DisplayName("판매자 정보에 필수 값이 없으면 복사본을 덮어쓰지 않고 이전 값을 그대로 둔다")
    void rejectChangeWithNull(RecommendReplicaSeller.ChangeInfo info) {
        RecommendReplicaSeller replica = RecommendReplicaSeller.create(RecommendReplicaSellerFixture.sellerApproved());

        assertThatThrownBy(() -> replica.applyIfNewer(info))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("필수입니다");
        assertThat(replica)
            .extracting(
                RecommendReplicaSeller::getBusinessName,
                RecommendReplicaSeller::getStatus,
                RecommendReplicaSeller::getEventOccurredAt)
            .containsExactly("소소한옷장", SellerStatus.ACTIVE, SELLER_CHANGED_AT);
    }

    static Stream<Named<RecommendReplicaSeller.ChangeInfo>> laterChangesWithNull() {
        LocalDateTime later = SELLER_CHANGED_AT.plusMinutes(1);
        return Stream.of(
            Named.of("판매자 정보", null),
            Named.of("상호명", RecommendReplicaSellerFixture.sellerChangeAt(null, SellerStatus.SUSPENDED, later)),
            Named.of("판매자 상태", RecommendReplicaSellerFixture.sellerChangeAt("새옷장", null, later)),
            Named.of("이벤트 발생 시각",
                RecommendReplicaSellerFixture.sellerChangeAt("새옷장", SellerStatus.SUSPENDED, null)));
    }
}
