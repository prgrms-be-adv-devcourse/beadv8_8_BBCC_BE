package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_NAME;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_SELLER_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_THUMBNAIL_URL;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.TODAY;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.childSizeInputWith;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.productAReplica;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.recommender;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.domain.recommended.RecommendedProduct;
import com.bbcc.kidly.boundedcontext.recommend.domain.recommended.RecommendedProductSelector;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaProduct;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.ChildSizeInput;
import com.bbcc.kidly.shared.childprofile.domain.BodyType;
import com.bbcc.kidly.shared.childprofile.domain.PreferredFit;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
}
