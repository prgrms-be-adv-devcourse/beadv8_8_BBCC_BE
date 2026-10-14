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
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

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

    @Test
    @DisplayName("색상만 다른 같은 사이즈 옵션은 한 줄로 묶고 처음 들어온 권장 키를 쓴다")
    void groupOptionsOfSameSize() {
        RecommendReplicaProduct replica = RecommendReplicaProduct.create(RecommendFixture.productAChangeOf(false,
            List.of(
                new RecommendReplicaProduct.OptionInfo("110", 105, 116),
                new RecommendReplicaProduct.OptionInfo("110", 106, 117),
                new RecommendReplicaProduct.OptionInfo("120", 113, 125)),
            PRODUCT_CHANGED_AT));

        assertThat(replica.toProductSizes().sizeRanges()).containsExactly(
            SizeRange.of("110", 105, 116),
            SizeRange.of("120", 113, 125));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("optionsWithoutHeightRange")
    @DisplayName("권장 키가 없는 옵션은 추천 후보가 될 수 없어 사이즈 줄에 넣지 않는다")
    void skipOptionWithoutHeightRange(boolean sizeFree, List<RecommendReplicaProduct.OptionInfo> options,
        ProductSizes expected) {
        RecommendReplicaProduct replica = RecommendReplicaProduct.create(
            RecommendFixture.productAChangeOf(sizeFree, options, PRODUCT_CHANGED_AT));

        assertThat(replica.toProductSizes()).isEqualTo(expected);
    }

    static Stream<Arguments> optionsWithoutHeightRange() {
        return Stream.of(
            Arguments.of(
                Named.of("사이즈 무관 상품의 FREE 옵션", true),
                List.of(new RecommendReplicaProduct.OptionInfo("FREE", null, null)),
                ProductSizes.of(true, List.of())),
            Arguments.of(
                Named.of("권장 키 하한이나 상한 하나만 빠진 옵션", false),
                List.of(
                    new RecommendReplicaProduct.OptionInfo("110", null, 115),
                    new RecommendReplicaProduct.OptionInfo("120", 115, null),
                    new RecommendReplicaProduct.OptionInfo("130", 125, 135)),
                ProductSizes.of(false, List.of(SizeRange.of("130", 125, 135)))));
    }
}
