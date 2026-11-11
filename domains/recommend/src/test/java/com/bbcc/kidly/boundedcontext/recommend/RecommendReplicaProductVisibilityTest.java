package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_CATEGORY_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_NAME;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_REGISTERED_AT;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_SELLER_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_THUMBNAIL_URL;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_CHANGED_AT;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.productOptionInfo;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaProduct;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaProduct.CategoryPathEntryInfo;
import com.bbcc.kidly.shared.childprofile.domain.Gender;
import com.bbcc.kidly.shared.product.domain.CategoryVisibleGender;
import com.bbcc.kidly.shared.product.domain.ProductStatus;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RecommendReplicaProductVisibilityTest {

    @Test
    @DisplayName("VIS-02 남아 자녀에게는 카테고리 경로의 어느 단계든 GIRL인 상품을 보여 주지 않는다")
    void girlCategoryProductIsHiddenFromBoy() {
        RecommendReplicaProduct dress = productWithPath(List.of(
            new CategoryPathEntryInfo(1L, "의류", CategoryVisibleGender.ALL),
            new CategoryPathEntryInfo(5L, "원피스", CategoryVisibleGender.GIRL)));
        RecommendReplicaProduct underGirlRoot = productWithPath(List.of(
            new CategoryPathEntryInfo(9L, "여아 전용관", CategoryVisibleGender.GIRL),
            new CategoryPathEntryInfo(10L, "블라우스", CategoryVisibleGender.ALL)));

        assertThat(List.of(dress, underGirlRoot)).allSatisfy(product ->
            assertThat(product.isVisibleTo(Gender.MALE)).isFalse());
    }

    @ParameterizedTest(name = "{0} 카테고리, {1} 자녀")
    @CsvSource({"ALL, MALE", "ALL, FEMALE", "GIRL, FEMALE", "GIRL, "})
    @DisplayName("VIS-02 남아 자녀에게는 ALL 카테고리 상품을, 여아 자녀에게는 모든 상품을 보여 주고, 성별을 모르면 거르지 않는다")
    void productIsVisibleByChildGender(CategoryVisibleGender leafVisibleGender, Gender childGender) {
        RecommendReplicaProduct product = productWithPath(List.of(
            new CategoryPathEntryInfo(1L, "의류", CategoryVisibleGender.ALL),
            new CategoryPathEntryInfo(5L, "원피스", leafVisibleGender)));

        assertThat(product.isVisibleTo(childGender)).isTrue();
    }

    @Test
    @DisplayName("VIS-01 카테고리 경로가 없는 예전 복사본은 남아 자녀에게도 보여 준다")
    void productWithoutCategoryPathIsVisibleToBoy() {
        assertThat(productWithPath(List.of()).isVisibleTo(Gender.MALE)).isTrue();
    }

    private static RecommendReplicaProduct productWithPath(List<CategoryPathEntryInfo> categoryPath) {
        return RecommendReplicaProduct.create(new RecommendReplicaProduct.ChangeInfo(PRODUCT_A_ID,
            PRODUCT_A_SELLER_ID, PRODUCT_A_CATEGORY_ID, PRODUCT_A_NAME, ProductStatus.ON_SALE,
            PRODUCT_A_THUMBNAIL_URL, PRODUCT_A_REGISTERED_AT, false, List.of(productOptionInfo("130", 125, 135)),
            null, null, List.of(), List.of(), PRODUCT_CHANGED_AT, categoryPath));
    }
}
