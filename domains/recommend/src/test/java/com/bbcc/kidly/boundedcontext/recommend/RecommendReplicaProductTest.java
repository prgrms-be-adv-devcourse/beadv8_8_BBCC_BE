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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

import com.bbcc.kidly.boundedcontext.recommend.domain.embedding.ProductEmbeddingText;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaCategoryPathEntry;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaProduct;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaSizeSpec;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.ProductSizes;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.SizeRange;
import com.bbcc.kidly.shared.product.domain.OptionSaleStatus;
import com.bbcc.kidly.shared.product.domain.ProductStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class RecommendReplicaProductTest {

    private static final LocalDateTime LATER = PRODUCT_CHANGED_AT.plusMinutes(1);

    private static final List<RecommendReplicaProduct.SizeSpecInfo> SIZE_130_SPECS = List.of(
        new RecommendReplicaProduct.SizeSpecInfo("130", 125, 135, Map.of("TOTAL_LENGTH", new BigDecimal("48"))));

    private static final List<RecommendReplicaProduct.CategoryPathEntryInfo> TOPS_PATH = List.of(
        new RecommendReplicaProduct.CategoryPathEntryInfo(1L, "상의"),
        new RecommendReplicaProduct.CategoryPathEntryInfo(11L, "티셔츠"));

    private static final List<RecommendReplicaProduct.OptionInfo> OPTIONS =
        List.of(productOptionInfo("130", 125, 135));

    // FREE 옵션은 권장 키가 없어 사이즈 줄에는 들어가지 않지만, 판매할 수 있으므로 판매 가능 사이즈에는 들어가야 한다
    private static final List<RecommendReplicaProduct.OptionInfo> ON_SALE_OPTIONS_WITH_FREE = List.of(
        productOptionInfo("110", 105, 115),
        productOptionInfo("FREE", null, null));

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
    @DisplayName("처음 받은 상품 정보로 만든 복사본에 브랜드 · 설명 · 사이즈별 실측이 들어간다")
    void createWithBrandDescriptionAndSizeSpecs() {
        RecommendReplicaProduct replica = RecommendReplicaProduct.create(RecommendFixture.productAChange());

        assertThat(replica.getBrand()).isEqualTo("키들리");
        assertThat(replica.getDescription()).isEqualTo("상품 설명");
        assertThat(replica.getSizeSpecs())
            .extracting(RecommendReplicaSizeSpec::getSize, RecommendReplicaSizeSpec::getMinHeightCm,
                RecommendReplicaSizeSpec::getMaxHeightCm, RecommendReplicaSizeSpec::getMeasurements)
            .containsExactly(
                tuple("110", 105, 115,
                    Map.of("TOTAL_LENGTH", new BigDecimal("42"), "CHEST_WIDTH", new BigDecimal("34"))),
                tuple("120", 115, 125,
                    Map.of("TOTAL_LENGTH", new BigDecimal("45"), "CHEST_WIDTH", new BigDecimal("36"))));
    }

    @Test
    @DisplayName("복사본을 만들 때 이벤트 값으로 임베딩 글을 채운다")
    void createWithEmbeddingText() {
        RecommendReplicaProduct replica = RecommendReplicaProduct.create(RecommendFixture.productAChange());

        assertThat(replica.getEmbeddingText()).isEqualTo("""
            상품명: 데일리 맨투맨
            브랜드: 키들리
            material: cotton
            season: fall, spring
            설명: 상품 설명""");
    }

    @Test
    @DisplayName("더 늦게 일어난 상품 변경으로 설명이 바뀌면 임베딩 글도 바뀐다")
    void rewriteEmbeddingTextWithLaterChange() {
        RecommendReplicaProduct replica = RecommendReplicaProduct.create(RecommendFixture.productAChange());

        replica.applyIfNewer(changeWithInfoValues("새 설명", List.of(), LATER));

        assertThat(replica.getEmbeddingText()).isEqualTo("""
            상품명: 데일리 맨투맨
            브랜드: 키들리
            설명: 새 설명""");
    }

    @Test
    @DisplayName("저장된 시각보다 이르게 일어난 상품 변경은 임베딩 글을 바꾸지 않는다")
    void keepEmbeddingTextWithEarlierChange() {
        RecommendReplicaProduct replica = RecommendReplicaProduct.create(RecommendFixture.productAChange());
        String stored = replica.getEmbeddingText();

        replica.applyIfNewer(changeWithInfoValues("새 설명", List.of(), PRODUCT_CHANGED_AT.minusMinutes(1)));

        assertThat(replica.getEmbeddingText()).isEqualTo(stored);
    }

    @Test
    @DisplayName("정보 값이 없는 예전 메시지의 상품 정보로도 정보 값 없이 임베딩 글을 만든다")
    void createEmbeddingTextWithoutInfoValues() {
        RecommendReplicaProduct replica = RecommendReplicaProduct.create(
            changeWithInfoValues("상품 설명", null, PRODUCT_CHANGED_AT));

        assertThat(replica.getEmbeddingText()).isEqualTo("""
            상품명: 데일리 맨투맨
            브랜드: 키들리
            설명: 상품 설명""");
    }

    @Test
    @DisplayName("상품 정보 변경 이벤트의 상품명을 복사본에 옮긴다")
    void copyProductName() {
        RecommendReplicaProduct replica = RecommendReplicaProduct.create(RecommendFixture.productAChange());

        assertThat(replica.getName()).isEqualTo(PRODUCT_A_NAME);
    }

    @Test
    @DisplayName("옵션 판매가가 여럿이면 가장 낮은 가격을 복사본의 가격으로 둔다")
    void keepLowestOptionPrice() {
        RecommendReplicaProduct replica = RecommendReplicaProduct.create(
            RecommendFixture.productAChangeWithPrices(30_000L, 25_000L, 28_000L));

        assertThat(replica.getMinPrice()).isEqualTo(25_000L);
    }

    @Test
    @DisplayName("옵션이 없으면 복사본의 가격을 비워 둔다")
    void leaveMinPriceEmptyWithoutOption() {
        RecommendReplicaProduct replica = RecommendReplicaProduct.create(RecommendFixture.productAChangeWithPrices());

        assertThat(replica.getMinPrice()).isNull();
    }

    @ParameterizedTest(name = "사이즈 {0}")
    @ValueSource(strings = {"110", "FREE"})
    @DisplayName("옵션이 판매중이고 품절이 아니면 그 사이즈는 판매 가능이다")
    void sellableSizeOfOnSaleOption(String size) {
        RecommendReplicaProduct replica = RecommendReplicaProduct.create(
            RecommendFixture.productAChangeOf(false, ON_SALE_OPTIONS_WITH_FREE, PRODUCT_CHANGED_AT));

        assertThat(replica.isSellable(size)).isTrue();
    }

    @Test
    @DisplayName("옵션에 없는 사이즈는 판매 가능이 아니다")
    void notSellableSizeWithoutOption() {
        RecommendReplicaProduct replica = RecommendReplicaProduct.create(
            RecommendFixture.productAChangeOf(false, ON_SALE_OPTIONS_WITH_FREE, PRODUCT_CHANGED_AT));

        assertThat(replica.isSellable("999")).isFalse();
    }

    @ParameterizedTest(name = "품절 {0}, 옵션 판매 상태 {1}")
    @CsvSource({"true, ON_SALE", "false, STOPPED"})
    @DisplayName("옵션이 품절이거나 옵션 판매 상태가 STOPPED이면 그 사이즈는 판매 가능이 아니다")
    void notSellableSizeOfSoldOutOrStoppedOption(boolean soldOut, OptionSaleStatus saleStatus) {
        RecommendReplicaProduct replica = RecommendReplicaProduct.create(RecommendFixture.productAChangeOf(false,
            List.of(productOptionInfo("110", soldOut, saleStatus)), PRODUCT_CHANGED_AT));

        assertThat(replica.isSellable("110")).isFalse();
    }

    // 판매 가능한 옵션을 가운데에 두어, 처음이나 마지막 옵션만 보고 정하면 실패하게 한다
    @Test
    @DisplayName("색상만 다른 같은 사이즈 옵션 중 하나라도 판매 가능이면 그 사이즈는 판매 가능이다")
    void sellableSizeWhenAnyOptionOfSameSizeIsSellable() {
        RecommendReplicaProduct replica = RecommendReplicaProduct.create(RecommendFixture.productAChangeOf(false,
            List.of(
                productOptionInfo("110", true, OptionSaleStatus.ON_SALE),
                productOptionInfo("110", false, OptionSaleStatus.ON_SALE),
                productOptionInfo("110", false, OptionSaleStatus.STOPPED)),
            PRODUCT_CHANGED_AT));

        assertThat(replica.isSellable("110")).isTrue();
    }

    @Test
    @DisplayName("색상만 다른 같은 사이즈 옵션이 모두 판매 가능이 아니면 그 사이즈는 판매 가능이 아니다")
    void notSellableSizeWhenNoOptionOfSameSizeIsSellable() {
        RecommendReplicaProduct replica = RecommendReplicaProduct.create(RecommendFixture.productAChangeOf(false,
            List.of(
                productOptionInfo("110", true, OptionSaleStatus.ON_SALE),
                productOptionInfo("110", false, OptionSaleStatus.STOPPED)),
            PRODUCT_CHANGED_AT));

        assertThat(replica.isSellable("110")).isFalse();
    }

    // 사이즈 무관 상품은 사이즈로 판매 가능을 물을 수 없어, 판매 가능한 옵션이 있는지로 판단한다
    @Test
    @DisplayName("판매 가능한 옵션이 하나라도 있으면 판매 가능한 옵션이 있는 상품이다")
    void hasSellableOptionWhenAnyOptionIsSellable() {
        RecommendReplicaProduct replica = RecommendReplicaProduct.create(RecommendFixture.productAChangeOf(true,
            List.of(productOptionInfo("FREE", null, null)), PRODUCT_CHANGED_AT));

        assertThat(replica.hasSellableOption()).isTrue();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("optionsWithoutSellable")
    @DisplayName("판매 가능한 옵션이 하나도 없으면 판매 가능한 옵션이 없는 상품이다")
    void hasNoSellableOptionWhenNoOptionIsSellable(List<RecommendReplicaProduct.OptionInfo> options) {
        RecommendReplicaProduct replica = RecommendReplicaProduct.create(
            RecommendFixture.productAChangeOf(false, options, PRODUCT_CHANGED_AT));

        assertThat(replica.hasSellableOption()).isFalse();
    }

    static Stream<Named<List<RecommendReplicaProduct.OptionInfo>>> optionsWithoutSellable() {
        return Stream.of(
            Named.of("모두 품절", List.of(
                productOptionInfo("110", true, OptionSaleStatus.ON_SALE),
                productOptionInfo("120", true, OptionSaleStatus.ON_SALE))),
            Named.of("모두 판매 중지", List.of(
                productOptionInfo("110", false, OptionSaleStatus.STOPPED),
                productOptionInfo("120", false, OptionSaleStatus.STOPPED))),
            Named.of("옵션 없음", List.of()));
    }

    @Test
    @DisplayName("색상만 다른 같은 사이즈 옵션은 한 줄로 묶고 처음 들어온 권장 키를 쓴다")
    void groupOptionsOfSameSize() {
        RecommendReplicaProduct replica = RecommendReplicaProduct.create(RecommendFixture.productAChangeOf(false,
            List.of(
                productOptionInfo("110", 105, 116),
                productOptionInfo("110", 106, 117),
                productOptionInfo("120", 113, 125)),
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

    // 사이즈 역할 항목이 없는 유형의 상품은 옵션 사이즈가 null로 오고, 이벤트의 사이즈 무관 값은 false로 온다.
    // 권장 키가 실려 와도 사이즈가 없으면 아이 키로 고를 사이즈가 아니므로 사이즈 줄에 넣지 않는다
    @Test
    @DisplayName("옵션 사이즈가 모두 없으면 사이즈 무관 상품으로 본다")
    void treatProductWithoutSizeAsSizeFree() {
        RecommendReplicaProduct replica = RecommendReplicaProduct.create(RecommendFixture.productAChangeOf(false,
            List.of(productOptionInfo(null, null, null), productOptionInfo(null, 105, 115)), PRODUCT_CHANGED_AT));

        assertThat(replica.toProductSizes()).isEqualTo(ProductSizes.of(true, List.of()));
    }

    @Test
    @DisplayName("INV-R01 더 늦게 일어난 상품 변경으로 덮어쓰고 사이즈 줄도 새 옵션으로 바꾼다")
    void applyLaterChange() {
        RecommendReplicaProduct replica = RecommendReplicaProduct.create(RecommendFixture.productAChange());

        replica.applyIfNewer(new RecommendReplicaProduct.ChangeInfo(PRODUCT_A_ID, 61L, 62L, PRODUCT_A_NAME,
            ProductStatus.STOPPED, "https://cdn.kidly.test/products/31-new.jpg", LocalDateTime.of(2026, 10, 2, 9, 0),
            true, List.of(productOptionInfo("130", 125, 135)), null, null, List.of(), List.of(), LATER));

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
                "https://cdn.kidly.test/products/31-new.jpg", LocalDateTime.of(2026, 10, 2, 9, 0), LATER);
        assertThat(replica.toProductSizes())
            .isEqualTo(ProductSizes.of(true, List.of(SizeRange.of("130", 125, 135))));
    }

    @Test
    @DisplayName("INV-R01 더 늦게 일어난 상품 변경으로 브랜드 · 설명 · 사이즈별 실측을 덮어쓰고, 빠진 사이즈의 실측은 남지 않는다")
    void applyLaterBrandDescriptionAndSizeSpecs() {
        RecommendReplicaProduct replica = RecommendReplicaProduct.create(RecommendFixture.productAChange());

        replica.applyIfNewer(changeWithDetails("키들리 키즈", "새 설명", SIZE_130_SPECS, LATER));

        assertThat(replica.getBrand()).isEqualTo("키들리 키즈");
        assertThat(replica.getDescription()).isEqualTo("새 설명");
        assertThat(replica.getSizeSpecs())
            .extracting(RecommendReplicaSizeSpec::getSize, RecommendReplicaSizeSpec::getMeasurements)
            .containsExactly(tuple("130", Map.of("TOTAL_LENGTH", new BigDecimal("48"))));
    }

    @Test
    @DisplayName("INV-R01 저장된 시각보다 이르게 일어난 상품 변경은 브랜드 · 설명 · 사이즈별 실측을 바꾸지 않는다")
    void ignoreEarlierBrandDescriptionAndSizeSpecs() {
        RecommendReplicaProduct replica = RecommendReplicaProduct.create(RecommendFixture.productAChange());

        replica.applyIfNewer(changeWithDetails("키들리 키즈", "새 설명", SIZE_130_SPECS,
            PRODUCT_CHANGED_AT.minusMinutes(1)));

        assertThat(replica.getBrand()).isEqualTo("키들리");
        assertThat(replica.getDescription()).isEqualTo("상품 설명");
        assertThat(replica.getSizeSpecs()).extracting(RecommendReplicaSizeSpec::getSize).containsExactly("110", "120");
    }

    @Test
    @DisplayName("브랜드 · 설명 · 사이즈별 실측이 없는 예전 메시지의 상품 정보로도 복사본을 만들고, 세 값은 비워 둔다")
    void createWithoutBrandDescriptionAndSizeSpecs() {
        RecommendReplicaProduct replica = RecommendReplicaProduct.create(
            changeWithDetails(null, null, List.of(), PRODUCT_CHANGED_AT));

        assertThat(replica.getBrand()).isNull();
        assertThat(replica.getDescription()).isNull();
        assertThat(replica.getSizeSpecs()).isEmpty();
    }

    @Test
    @DisplayName("처음 받은 상품 정보로 만든 복사본에 카테고리 경로가 대분류부터 종류 순서대로 들어간다")
    void createWithCategoryPath() {
        RecommendReplicaProduct replica = RecommendReplicaProduct.create(
            changeWithCategoryPath(TOPS_PATH, PRODUCT_CHANGED_AT));

        assertThat(replica.getCategoryPath())
            .extracting(RecommendReplicaCategoryPathEntry::getCategoryId,
                RecommendReplicaCategoryPathEntry::getCategoryName)
            .containsExactly(tuple(1L, "상의"), tuple(11L, "티셔츠"));
    }

    @Test
    @DisplayName("INV-R01 더 늦게 일어난 상품 변경으로 카테고리 경로를 새 값으로 바꾸고, 줄어든 칸은 남지 않는다")
    void applyLaterCategoryPath() {
        RecommendReplicaProduct replica = RecommendReplicaProduct.create(
            changeWithCategoryPath(TOPS_PATH, PRODUCT_CHANGED_AT));

        replica.applyIfNewer(changeWithCategoryPath(
            List.of(new RecommendReplicaProduct.CategoryPathEntryInfo(2L, "하의")), LATER));

        assertThat(replica.getCategoryPath())
            .extracting(RecommendReplicaCategoryPathEntry::getCategoryId,
                RecommendReplicaCategoryPathEntry::getCategoryName)
            .containsExactly(tuple(2L, "하의"));
    }

    @Test
    @DisplayName("INV-R01 저장된 시각보다 이르게 일어난 상품 변경은 카테고리 경로를 바꾸지 않는다")
    void ignoreEarlierCategoryPath() {
        RecommendReplicaProduct replica = RecommendReplicaProduct.create(
            changeWithCategoryPath(TOPS_PATH, PRODUCT_CHANGED_AT));

        replica.applyIfNewer(changeWithCategoryPath(
            List.of(new RecommendReplicaProduct.CategoryPathEntryInfo(2L, "하의")), PRODUCT_CHANGED_AT.minusMinutes(1)));

        assertThat(replica.getCategoryPath())
            .extracting(RecommendReplicaCategoryPathEntry::getCategoryId).containsExactly(1L, 11L);
    }

    @Test
    @DisplayName("카테고리 경로가 비어 있는 상품 정보로도 복사본을 만들고, 경로는 비워 둔다")
    void createWithEmptyCategoryPath() {
        RecommendReplicaProduct replica = RecommendReplicaProduct.create(
            changeWithCategoryPath(List.of(), PRODUCT_CHANGED_AT));

        assertThat(replica.getCategoryPath()).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("changesNotLater")
    @DisplayName("INV-R01 저장된 시각과 같거나 이르게 일어난 상품 변경은 필수 값이 없어도 무시한다")
    void ignoreChangeNotLater(RecommendReplicaProduct.ChangeInfo change) {
        RecommendReplicaProduct replica = RecommendReplicaProduct.create(RecommendFixture.productAChange());

        replica.applyIfNewer(change);

        assertThat(replica.getEventOccurredAt()).isEqualTo(PRODUCT_CHANGED_AT);
        assertThat(replica.toProductSizes()).isEqualTo(ProductSizes.of(false, List.of(
            SizeRange.of("110", 105, 115),
            SizeRange.of("120", 115, 125))));
    }

    // 이미 반영한 이벤트를 다시 받거나 이전 이벤트가 늦게 도착한 경우다. 값이 비어 있어도 예외 없이 무시해야 한다
    static Stream<Named<RecommendReplicaProduct.ChangeInfo>> changesNotLater() {
        LocalDateTime earlier = PRODUCT_CHANGED_AT.minusMinutes(1);
        List<RecommendReplicaProduct.OptionInfo> newOptions = List.of(productOptionInfo("130", 125, 135));
        return Stream.of(
            Named.of("같은 시각", RecommendFixture.productAChangeOf(true, newOptions, PRODUCT_CHANGED_AT)),
            Named.of("1분 이른 시각", RecommendFixture.productAChangeOf(true, newOptions, earlier)),
            Named.of("같은 시각 · 판매자 ID 없음",
                change(PRODUCT_A_ID, null, ProductStatus.ON_SALE, PRODUCT_A_REGISTERED_AT, newOptions,
                    PRODUCT_CHANGED_AT)),
            Named.of("1분 이른 시각 · 판매 상태 없음",
                change(PRODUCT_A_ID, PRODUCT_A_SELLER_ID, null, PRODUCT_A_REGISTERED_AT, newOptions, earlier)),
            Named.of("1분 이른 시각 · 상품 등록 시각 없음",
                change(PRODUCT_A_ID, PRODUCT_A_SELLER_ID, ProductStatus.ON_SALE, null, newOptions, earlier)),
            Named.of("1분 이른 시각 · 옵션 목록 없음",
                change(PRODUCT_A_ID, PRODUCT_A_SELLER_ID, ProductStatus.ON_SALE, PRODUCT_A_REGISTERED_AT, null,
                    earlier)));
    }

    // 처음 상품 변경은 110 · 120 옵션이 모두 25,000원이고 판매 가능하다. 110은 새 옵션에 없어 판매 가능이 아니게 된다
    @Test
    @DisplayName("INV-R01 더 늦게 일어난 상품 변경으로 상품명 · 최저가 · 판매 가능 사이즈를 덮어쓴다")
    void applyLaterChangeToNamePriceAndSellableSizes() {
        RecommendReplicaProduct replica = RecommendReplicaProduct.create(RecommendFixture.productAChange());

        replica.applyIfNewer(RecommendFixture.productAChangeNamed("데일리 기모 맨투맨",
            List.of(productOptionInfo("130", 125, 135, 27_000L), productOptionInfo("140", 135, 145, 29_000L)),
            LATER));

        assertThat(replica.getName()).isEqualTo("데일리 기모 맨투맨");
        assertThat(replica.getMinPrice()).isEqualTo(27_000L);
        assertThat(replica.isSellable("110")).isFalse();
        assertThat(replica.isSellable("130")).isTrue();
        assertThat(replica.isSellable("140")).isTrue();
    }

    @ParameterizedTest(name = "저장된 시각에서 {0}분")
    @ValueSource(longs = {0, -1})
    @DisplayName("INV-R01 저장된 시각과 같거나 이르게 일어난 상품 변경은 상품명 · 최저가 · 판매 가능 사이즈를 바꾸지 않는다")
    void ignoreChangeNotLaterForNamePriceAndSellableSizes(long minutes) {
        RecommendReplicaProduct replica = RecommendReplicaProduct.create(RecommendFixture.productAChange());

        replica.applyIfNewer(RecommendFixture.productAChangeNamed("데일리 기모 맨투맨",
            List.of(productOptionInfo("130", 125, 135, 27_000L)), PRODUCT_CHANGED_AT.plusMinutes(minutes)));

        assertThat(replica.getName()).isEqualTo(PRODUCT_A_NAME);
        assertThat(replica.getMinPrice()).isEqualTo(25_000L);
        assertThat(replica.isSellable("110")).isTrue();
        assertThat(replica.isSellable("130")).isFalse();
    }

    static Stream<Arguments> optionsWithoutHeightRange() {
        return Stream.of(
            Arguments.of(
                Named.of("사이즈 무관 상품의 FREE 옵션", true),
                List.of(productOptionInfo("FREE", null, null)),
                ProductSizes.of(true, List.of())),
            Arguments.of(
                Named.of("권장 키 하한이나 상한 하나만 빠진 옵션", false),
                List.of(
                    productOptionInfo("110", null, 115),
                    productOptionInfo("120", 115, null),
                    productOptionInfo("130", 125, 135)),
                ProductSizes.of(false, List.of(SizeRange.of("130", 125, 135)))));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("creationsWithNull")
    @DisplayName("상품 정보에 필수 값이 없으면 복사본을 만들지 않는다")
    void rejectCreationWithNull(ThrowingCallable creation) {
        assertThatThrownBy(creation)
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("필수입니다");
    }

    static Stream<Named<ThrowingCallable>> creationsWithNull() {
        return Stream.of(
            Named.of("상품 정보", () -> RecommendReplicaProduct.create(null)),
            Named.of("상품 ID", () -> RecommendReplicaProduct.create(
                change(null, PRODUCT_A_SELLER_ID, ProductStatus.ON_SALE, PRODUCT_A_REGISTERED_AT, OPTIONS, LATER))),
            Named.of("판매자 ID", () -> RecommendReplicaProduct.create(
                change(PRODUCT_A_ID, null, ProductStatus.ON_SALE, PRODUCT_A_REGISTERED_AT, OPTIONS, LATER))),
            Named.of("상품명", () -> RecommendReplicaProduct.create(changeWithoutName())),
            Named.of("판매 상태", () -> RecommendReplicaProduct.create(
                change(PRODUCT_A_ID, PRODUCT_A_SELLER_ID, null, PRODUCT_A_REGISTERED_AT, OPTIONS, LATER))),
            Named.of("상품 등록 시각", () -> RecommendReplicaProduct.create(
                change(PRODUCT_A_ID, PRODUCT_A_SELLER_ID, ProductStatus.ON_SALE, null, OPTIONS, LATER))),
            Named.of("옵션 목록", () -> RecommendReplicaProduct.create(
                change(PRODUCT_A_ID, PRODUCT_A_SELLER_ID, ProductStatus.ON_SALE, PRODUCT_A_REGISTERED_AT, null,
                    LATER))),
            Named.of("이벤트 발생 시각", () -> RecommendReplicaProduct.create(
                change(PRODUCT_A_ID, PRODUCT_A_SELLER_ID, ProductStatus.ON_SALE, PRODUCT_A_REGISTERED_AT, OPTIONS,
                    null))));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("changesWithNull")
    @DisplayName("상품 정보에 필수 값이 없으면 복사본을 덮어쓰지 않고 이전 값을 그대로 둔다")
    void rejectChangeWithNull(RecommendReplicaProduct.ChangeInfo change) {
        RecommendReplicaProduct replica = RecommendReplicaProduct.create(RecommendFixture.productAChange());

        assertThatThrownBy(() -> replica.applyIfNewer(change))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("필수입니다");
        assertThat(replica)
            .extracting(
                RecommendReplicaProduct::getSellerId,
                RecommendReplicaProduct::getStatus,
                RecommendReplicaProduct::getProductRegisteredAt,
                RecommendReplicaProduct::getEventOccurredAt)
            .containsExactly(PRODUCT_A_SELLER_ID, ProductStatus.ON_SALE, PRODUCT_A_REGISTERED_AT, PRODUCT_CHANGED_AT);
        assertThat(replica.toProductSizes()).isEqualTo(ProductSizes.of(false, List.of(
            SizeRange.of("110", 105, 115),
            SizeRange.of("120", 115, 125))));
    }

    static Stream<Named<RecommendReplicaProduct.ChangeInfo>> changesWithNull() {
        return Stream.of(
            Named.of("상품 정보", null),
            Named.of("판매자 ID",
                change(PRODUCT_A_ID, null, ProductStatus.ON_SALE, PRODUCT_A_REGISTERED_AT, OPTIONS, LATER)),
            Named.of("상품명", changeWithoutName()),
            Named.of("판매 상태",
                change(PRODUCT_A_ID, PRODUCT_A_SELLER_ID, null, PRODUCT_A_REGISTERED_AT, OPTIONS, LATER)),
            Named.of("상품 등록 시각",
                change(PRODUCT_A_ID, PRODUCT_A_SELLER_ID, ProductStatus.ON_SALE, null, OPTIONS, LATER)),
            Named.of("옵션 목록",
                change(PRODUCT_A_ID, PRODUCT_A_SELLER_ID, ProductStatus.ON_SALE, PRODUCT_A_REGISTERED_AT, null, LATER)),
            Named.of("이벤트 발생 시각", change(PRODUCT_A_ID, PRODUCT_A_SELLER_ID, ProductStatus.ON_SALE,
                PRODUCT_A_REGISTERED_AT, OPTIONS, null)),
            Named.of("실측 줄의 사이즈", changeWithDetails("키들리", "상품 설명", List.of(
                new RecommendReplicaProduct.SizeSpecInfo(null, 105, 115, Map.of("TOTAL_LENGTH", new BigDecimal("42")))),
                LATER)),
            Named.of("실측 줄의 실측 값", changeWithDetails("키들리", "상품 설명", List.of(
                new RecommendReplicaProduct.SizeSpecInfo("110", 105, 115, null)), LATER)),
            Named.of("카테고리 경로 칸의 ID", changeWithCategoryPath(
                List.of(new RecommendReplicaProduct.CategoryPathEntryInfo(null, "상의")), LATER)),
            Named.of("카테고리 경로 칸의 이름", changeWithCategoryPath(
                List.of(new RecommendReplicaProduct.CategoryPathEntryInfo(1L, null)), LATER)));
    }

    // 상품명만 빠진, 저장된 시각보다 늦은 상품 변경이다
    private static RecommendReplicaProduct.ChangeInfo changeWithoutName() {
        return new RecommendReplicaProduct.ChangeInfo(PRODUCT_A_ID, PRODUCT_A_SELLER_ID, PRODUCT_A_CATEGORY_ID, null,
            ProductStatus.ON_SALE, PRODUCT_A_THUMBNAIL_URL, PRODUCT_A_REGISTERED_AT, false, OPTIONS, null, null,
            List.of(), List.of(), LATER);
    }

    // 상품 A의 다른 값은 그대로 두고 브랜드 · 설명 · 사이즈별 실측과 발생 시각만 정해 만든다
    private static RecommendReplicaProduct.ChangeInfo changeWithDetails(String brand, String description,
        List<RecommendReplicaProduct.SizeSpecInfo> sizeSpecs, LocalDateTime occurredAt) {
        return new RecommendReplicaProduct.ChangeInfo(PRODUCT_A_ID, PRODUCT_A_SELLER_ID, PRODUCT_A_CATEGORY_ID,
            PRODUCT_A_NAME, ProductStatus.ON_SALE, PRODUCT_A_THUMBNAIL_URL, PRODUCT_A_REGISTERED_AT, false, OPTIONS,
            brand, description, sizeSpecs, List.of(), occurredAt);
    }

    private static RecommendReplicaProduct.ChangeInfo changeWithInfoValues(String description,
        List<ProductEmbeddingText.InfoValue> infoValues, LocalDateTime occurredAt) {
        return new RecommendReplicaProduct.ChangeInfo(PRODUCT_A_ID, PRODUCT_A_SELLER_ID, PRODUCT_A_CATEGORY_ID,
            PRODUCT_A_NAME, ProductStatus.ON_SALE, PRODUCT_A_THUMBNAIL_URL, PRODUCT_A_REGISTERED_AT, false, OPTIONS,
            "키들리", description, List.of(), infoValues, occurredAt);
    }

    // 상품 A의 다른 값은 그대로 두고 카테고리 경로와 발생 시각만 정해 만든다
    private static RecommendReplicaProduct.ChangeInfo changeWithCategoryPath(
        List<RecommendReplicaProduct.CategoryPathEntryInfo> categoryPath, LocalDateTime occurredAt) {
        return new RecommendReplicaProduct.ChangeInfo(PRODUCT_A_ID, PRODUCT_A_SELLER_ID, PRODUCT_A_CATEGORY_ID,
            PRODUCT_A_NAME, ProductStatus.ON_SALE, PRODUCT_A_THUMBNAIL_URL, PRODUCT_A_REGISTERED_AT, false, OPTIONS,
            null, null, List.of(), occurredAt, categoryPath);
    }

    private static RecommendReplicaProduct.ChangeInfo change(Long productId, Long sellerId, ProductStatus status,
        LocalDateTime registeredAt, List<RecommendReplicaProduct.OptionInfo> options, LocalDateTime occurredAt) {
        return new RecommendReplicaProduct.ChangeInfo(productId, sellerId, PRODUCT_A_CATEGORY_ID, PRODUCT_A_NAME,
            status, PRODUCT_A_THUMBNAIL_URL, registeredAt, false, options, null, null, List.of(), List.of(),
            occurredAt);
    }
}
