package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_NAME;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_SELLER_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_THUMBNAIL_URL;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.TODAY;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.childSizeInputWith;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.childSizeInputWithHeight;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.productAReplica;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.productAReplicaWith;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.productAReplicaWithStatus;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.productOptionInfo;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.recommender;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.domain.recommended.RecommendedProduct;
import com.bbcc.kidly.boundedcontext.recommend.domain.recommended.RecommendedProductSelector;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaProduct;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.ChildSizeInput;
import com.bbcc.kidly.shared.childprofile.domain.BodyType;
import com.bbcc.kidly.shared.childprofile.domain.PreferredFit;
import com.bbcc.kidly.shared.product.domain.OptionSaleStatus;
import com.bbcc.kidly.shared.product.domain.ProductStatus;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class RecommendedProductSelectorTest {

    private final RecommendedProductSelector selector = new RecommendedProductSelector(recommender());

    @Test
    @DisplayName("맞는 사이즈가 있고 그 사이즈를 판매할 수 있는 상품은 추천 사이즈와 함께 목록에 담는다")
    void selectProductWithSellableRecommendedSize() {
        // 키 115cm는 110과 120 두 후보에 들고, 선호 핏이 슬림이라 둘 중 하나가 골라진다
        ChildSizeInput child = childSizeInputWith(115.0, 20.0, BodyType.NORMAL, PreferredFit.SLIM);
        // 상품 A의 옵션 110과 120은 모두 판매가 25,000원이라 최저가도 25,000원이다
        RecommendReplicaProduct product = productAReplica();
        String expectedSize = recommender()
            .recommend(child, product.toProductSizes(), TODAY)
            .getRecommendedSize();

        List<RecommendedProduct> result = selector.select(child, List.of(product), TODAY);

        assertThat(result).containsExactly(new RecommendedProduct(
            PRODUCT_A_ID,
            PRODUCT_A_SELLER_ID,
            PRODUCT_A_NAME,
            PRODUCT_A_THUMBNAIL_URL,
            25_000L,
            expectedSize));
    }

    @Test
    @DisplayName("맞는 사이즈가 없는 상품은 목록에서 뺀다")
    void excludeProductWithoutMatchingSize() {
        // 키 160cm는 110(105 ~ 115cm)과 120(115 ~ 125cm) 모두에서 2cm보다 멀어 맞는 사이즈가 없다
        ChildSizeInput child = childSizeInputWithHeight(160.0);
        RecommendReplicaProduct product = productAReplica();
        assertThat(recommender().recommend(child, product.toProductSizes(), TODAY).getRecommendedSize()).isNull();

        List<RecommendedProduct> result = selector.select(child, List.of(product), TODAY);

        assertThat(result).isEmpty();
    }

    @ParameterizedTest(name = "품절 {0}, 옵션 판매 상태 {1}")
    @CsvSource({
        "true, ON_SALE",
        "false, STOPPED",
    })
    @DisplayName("추천 사이즈가 판매 가능하지 않으면 목록에서 뺀다")
    void excludeProductWhenRecommendedSizeIsNotSellable(boolean soldOut, OptionSaleStatus saleStatus) {
        // 상품에는 권장 키 105 ~ 115cm인 110 옵션 하나만 있어, 키 115cm 자녀의 추천 사이즈는 110이다
        ChildSizeInput child = childSizeInputWithHeight(115.0);
        RecommendReplicaProduct product = productAReplicaWith(List.of(productOptionInfo("110", soldOut, saleStatus)));
        assertThat(recommender().recommend(child, product.toProductSizes(), TODAY).getRecommendedSize())
            .isEqualTo("110");

        List<RecommendedProduct> result = selector.select(child, List.of(product), TODAY);

        assertThat(result).isEmpty();
    }

    @ParameterizedTest(name = "상품 판매 상태 {0}")
    @EnumSource(value = ProductStatus.class, mode = EnumSource.Mode.EXCLUDE, names = "ON_SALE")
    @DisplayName("상품 판매 상태가 판매중이 아니면 목록에서 뺀다")
    void excludeProductNotOnSale(ProductStatus status) {
        // 상품 판매 상태만 다르고, 맞는 사이즈가 있고 그 사이즈 옵션도 판매할 수 있는 상품이다
        ChildSizeInput child = childSizeInputWithHeight(115.0);
        RecommendReplicaProduct product = productAReplicaWithStatus(status);
        String recommendedSize = recommender()
            .recommend(child, product.toProductSizes(), TODAY)
            .getRecommendedSize();
        assertThat(recommendedSize).isNotNull();
        assertThat(product.isSellable(recommendedSize)).isTrue();

        List<RecommendedProduct> result = selector.select(child, List.of(product), TODAY);

        assertThat(result).isEmpty();
    }
}
