package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_BRAND;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_CATEGORY_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_NAME;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_REGISTERED_AT;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_SELLER_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_THUMBNAIL_URL;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_CHANGED_AT;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.productATopReplica;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.productOptionInfo;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaProduct;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaProduct.CategoryPathEntryInfo;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.ReplicaProductSnapshot;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.ProductSizes;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.SizeRange;
import com.bbcc.kidly.shared.childprofile.domain.Gender;
import com.bbcc.kidly.shared.product.domain.CategoryVisibleGender;
import com.bbcc.kidly.shared.product.domain.ProductStatus;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ReplicaProductSnapshotTest {

    @Test
    @DisplayName("상품 복사본에서 목록 계산에 쓰는 값을 그대로 옮긴다")
    void copyValuesFromReplica() {
        // 상품 A는 110(권장 키 105 ~ 115cm)과 120(115 ~ 125cm) 옵션이 모두 판매가 25,000원에 판매 중이고, 경로는 「의류 > 상의」다
        ReplicaProductSnapshot snapshot = ReplicaProductSnapshot.from(productATopReplica());

        assertThat(snapshot.productId()).isEqualTo(PRODUCT_A_ID);
        assertThat(snapshot.sellerId()).isEqualTo(PRODUCT_A_SELLER_ID);
        assertThat(snapshot.categoryId()).isEqualTo(PRODUCT_A_CATEGORY_ID);
        assertThat(snapshot.name()).isEqualTo(PRODUCT_A_NAME);
        assertThat(snapshot.thumbnailUrl()).isEqualTo(PRODUCT_A_THUMBNAIL_URL);
        assertThat(snapshot.minPrice()).isEqualTo(25_000L);
        assertThat(snapshot.status()).isEqualTo(ProductStatus.ON_SALE);
        assertThat(snapshot.brand()).isEqualTo(PRODUCT_A_BRAND);
        assertThat(snapshot.productRegisteredAt()).isEqualTo(PRODUCT_A_REGISTERED_AT);
        assertThat(snapshot.saleStartedAt()).isNull();
        assertThat(snapshot.sizes()).isEqualTo(ProductSizes.of(false, List.of(
            SizeRange.of("110", 105, 115),
            SizeRange.of("120", 115, 125))));
        assertThat(snapshot.isSellable("110")).isTrue();
        assertThat(snapshot.isSellable("120")).isTrue();
        assertThat(snapshot.isSellable("130")).isFalse();
        assertThat(snapshot.hasSellableOption()).isTrue();
        assertThat(snapshot.categoryPathNames()).containsExactly("의류", "상의");
    }

    @Test
    @DisplayName("VIS-02 남아 자녀에게는 카테고리 경로의 어느 단계든 GIRL인 상품을 보여 주지 않는다")
    void girlCategoryProductIsHiddenFromBoy() {
        ReplicaProductSnapshot dress = snapshotWithPath(List.of(
            new CategoryPathEntryInfo(1L, "의류", CategoryVisibleGender.ALL),
            new CategoryPathEntryInfo(5L, "원피스", CategoryVisibleGender.GIRL)));
        ReplicaProductSnapshot underGirlRoot = snapshotWithPath(List.of(
            new CategoryPathEntryInfo(9L, "여아 전용관", CategoryVisibleGender.GIRL),
            new CategoryPathEntryInfo(10L, "블라우스", CategoryVisibleGender.ALL)));

        assertThat(dress.isVisibleTo(Gender.MALE)).isFalse();
        assertThat(underGirlRoot.isVisibleTo(Gender.MALE)).isFalse();
    }

    @ParameterizedTest(name = "{0} 카테고리, {1} 자녀")
    @CsvSource({"ALL, MALE", "ALL, FEMALE", "GIRL, FEMALE", "GIRL, "})
    @DisplayName("VIS-02 남아 자녀에게는 ALL 카테고리 상품을, 여아 자녀에게는 모든 상품을 보여 주고, 성별을 모르면 거르지 않는다")
    void productIsVisibleByChildGender(CategoryVisibleGender leafVisibleGender, Gender childGender) {
        ReplicaProductSnapshot snapshot = snapshotWithPath(List.of(
            new CategoryPathEntryInfo(1L, "의류", CategoryVisibleGender.ALL),
            new CategoryPathEntryInfo(5L, "원피스", leafVisibleGender)));

        assertThat(snapshot.isVisibleTo(childGender)).isTrue();
    }

    @Test
    @DisplayName("VIS-01 카테고리 경로가 없는 예전 복사본은 남아 자녀에게도 보여 준다")
    void productWithoutCategoryPathIsVisibleToBoy() {
        assertThat(snapshotWithPath(List.of()).isVisibleTo(Gender.MALE)).isTrue();
    }

    private static ReplicaProductSnapshot snapshotWithPath(List<CategoryPathEntryInfo> categoryPath) {
        return ReplicaProductSnapshot.from(RecommendReplicaProduct.create(new RecommendReplicaProduct.ChangeInfo(
            PRODUCT_A_ID, PRODUCT_A_SELLER_ID, PRODUCT_A_CATEGORY_ID, PRODUCT_A_NAME, ProductStatus.ON_SALE,
            PRODUCT_A_THUMBNAIL_URL, PRODUCT_A_REGISTERED_AT, false, List.of(productOptionInfo("130", 125, 135)),
            null, null, List.of(), List.of(), PRODUCT_CHANGED_AT, categoryPath)));
    }
}
