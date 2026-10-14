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
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

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

    @Test
    @DisplayName("INV-R01 더 늦게 일어난 상품 변경으로 덮어쓰고 사이즈 줄도 새 옵션으로 바꾼다")
    void applyLaterChange() {
        RecommendReplicaProduct replica = RecommendReplicaProduct.create(RecommendFixture.productAChange());
        LocalDateTime later = PRODUCT_CHANGED_AT.plusMinutes(1);

        replica.applyIfNewer(new RecommendReplicaProduct.ChangeInfo(PRODUCT_A_ID, 61L, 62L, ProductStatus.STOPPED,
            "https://cdn.kidly.test/products/31-new.jpg", LocalDateTime.of(2026, 10, 2, 9, 0), true,
            List.of(new RecommendReplicaProduct.OptionInfo("130", 125, 135)), later));

        assertThat(replica)
            .extracting(
                RecommendReplicaProduct::getProductId,
                RecommendReplicaProduct::getSellerId,
                RecommendReplicaProduct::getCategoryId,
                RecommendReplicaProduct::getStatus,
                RecommendReplicaProduct::getThumbnailUrl,
                RecommendReplicaProduct::getProductRegisteredAt,
                RecommendReplicaProduct::getEventOccurredAt)
            .containsExactly(PRODUCT_A_ID, 61L, 62L, ProductStatus.STOPPED,
                "https://cdn.kidly.test/products/31-new.jpg", LocalDateTime.of(2026, 10, 2, 9, 0), later);
        assertThat(replica.toProductSizes())
            .isEqualTo(ProductSizes.of(true, List.of(SizeRange.of("130", 125, 135))));
    }

    @ParameterizedTest(name = "저장된 시각에서 {0}분")
    @ValueSource(longs = {0, -1})
    @DisplayName("INV-R01 저장된 시각과 같거나 이르게 일어난 상품 변경은 무시한다")
    void ignoreChangeNotLater(long minutes) {
        RecommendReplicaProduct replica = RecommendReplicaProduct.create(RecommendFixture.productAChange());

        replica.applyIfNewer(RecommendFixture.productAChangeOf(true,
            List.of(new RecommendReplicaProduct.OptionInfo("130", 125, 135)),
            PRODUCT_CHANGED_AT.plusMinutes(minutes)));

        assertThat(replica.getEventOccurredAt()).isEqualTo(PRODUCT_CHANGED_AT);
        assertThat(replica.toProductSizes()).isEqualTo(ProductSizes.of(false, List.of(
            SizeRange.of("110", 105, 115),
            SizeRange.of("120", 115, 125))));
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
