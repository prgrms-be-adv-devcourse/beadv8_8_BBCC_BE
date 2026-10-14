package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_CATEGORY_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_REGISTERED_AT;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_SELLER_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_THUMBNAIL_URL;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_CHANGED_AT;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaProduct;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.ProductSizes;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.SizeRange;
import com.bbcc.kidly.shared.product.domain.ProductStatus;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RecommendReplicaProductTest {

    @Test
    @DisplayName("처음 받은 상품 정보로 복사본을 만든다")
    void createWithFirstChange() {
        RecommendReplicaProduct replica = RecommendReplicaProduct.create(RecommendFixture.productAChange());

        assertThat(replica)
            .extracting(
                RecommendReplicaProduct::getProductId,
                RecommendReplicaProduct::getSellerId,
                RecommendReplicaProduct::getCategoryId,
                RecommendReplicaProduct::getStatus,
                RecommendReplicaProduct::getThumbnailUrl,
                RecommendReplicaProduct::getProductRegisteredAt,
                RecommendReplicaProduct::getEventOccurredAt)
            .containsExactly(PRODUCT_A_ID, PRODUCT_A_SELLER_ID, PRODUCT_A_CATEGORY_ID, ProductStatus.ON_SALE,
                PRODUCT_A_THUMBNAIL_URL, PRODUCT_A_REGISTERED_AT, PRODUCT_CHANGED_AT);
        assertThat(replica.toProductSizes()).isEqualTo(ProductSizes.of(false, List.of(
            SizeRange.of("110", 105, 115),
            SizeRange.of("120", 115, 125))));
    }
}
