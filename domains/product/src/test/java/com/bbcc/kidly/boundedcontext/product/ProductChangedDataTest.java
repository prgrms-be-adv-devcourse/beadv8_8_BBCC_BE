package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProductFixture.SELLER_ID;
import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.CLOTHING_PRICE;
import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.addOptions;
import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.clothingOptions;
import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.option;
import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.productA;
import static com.bbcc.kidly.boundedcontext.product.SizeSpecFixture.addSizeSpecs;
import static com.bbcc.kidly.boundedcontext.product.SizeSpecFixture.spec;
import static com.bbcc.kidly.boundedcontext.product.SizeSpecFixture.top110Measurements;
import static com.bbcc.kidly.boundedcontext.product.SizeSpecFixture.top120Measurements;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.ClothingOptions;
import com.bbcc.kidly.boundedcontext.product.domain.product.Product;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductChangedData;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductOptionStatus;
import com.bbcc.kidly.boundedcontext.product.domain.type.Category;
import com.bbcc.kidly.shared.product.domain.OptionSaleStatus;
import com.bbcc.kidly.shared.product.domain.ProductStatus;
import com.bbcc.kidly.shared.product.event.ProductChangedEvent;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 상품 정보 변경됨에 담을 값을 상품 애그리거트와 옵션별 판매 가능 수량으로 만드는지 확인한다.
 * 저장하지 않은 엔티티라 ID와 등록 시각은 테스트에서 직접 넣는다.
 */
class ProductChangedDataTest {

    private static final long PRODUCT_ID = 1L;
    private static final long CATEGORY_ID = 5L;
    private static final long CLOTHING_CATEGORY_ID = 4L;
    private static final long OPTION_110_ID = 11L;
    private static final long OPTION_120_ID = 12L;
    private static final long NAVY_ID = 21L;
    private static final long SIZE_110_ID = 22L;
    private static final long SIZE_120_ID = 23L;
    private static final long COTTON_ID = 31L;
    private static final long BRUSHED_ID = 32L;
    private static final long STRETCH_ID = 33L;
    private static final LocalDateTime REGISTERED_AT = LocalDateTime.of(2026, 10, 1, 9, 0);

    private final ClothingOptions clothing = clothingOptions();
    private final Product product = productA(clothing);
    private final Category top = CategoryFixture.top(CategoryFixture.clothing(), clothing.type());

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(product, "id", PRODUCT_ID);
        ReflectionTestUtils.setField(product, "createdAt", REGISTERED_AT);
        ReflectionTestUtils.setField(ReflectionTestUtils.getField(product, "category"), "id", CATEGORY_ID);
    }

    @Test
    @DisplayName("SALE-08 옵션마다 옵션 이름 · 사이즈 · 판매가 · 그 사이즈의 권장 키 · 품절 · 판매 상태를 담는다")
    void carryOptionsWithHeightsAndSoldOut() {
        withOptionsAndSizeSpecs110And120();
        product.startSale(SELLER_ID, top, clothing.type());

        ProductChangedEvent.Data data = ProductChangedData.of(product, top,
            Map.of(OPTION_110_ID, 10, OPTION_120_ID, 0));

        // 옵션 값 · 상품 정보 값 · 브랜드 · 설명 · 사이즈별 실측 · 카테고리 경로는 다른 테스트가 확인하므로 여기서는 비교하지 않는다
        assertThat(data).usingRecursiveComparison()
            .ignoringFields("infoValues", "options.values", "brand", "description", "sizeSpecs", "categoryPath")
            .isEqualTo(new ProductChangedEvent.Data(
                PRODUCT_ID, SELLER_ID, "상품 A", CATEGORY_ID, ProductStatus.ON_SALE, product.getThumbnailUrl(),
                REGISTERED_AT, false, List.of(
                    new ProductChangedEvent.Option(OPTION_110_ID, "색상: 네이비 / 사이즈: 110", "110", CLOTHING_PRICE,
                        105, 115, false, OptionSaleStatus.ON_SALE),
                    new ProductChangedEvent.Option(OPTION_120_ID, "색상: 네이비 / 사이즈: 120", "120", CLOTHING_PRICE,
                        115, 125, true, OptionSaleStatus.ON_SALE))));
    }

    @Test
    @DisplayName("SALE-08 옵션마다 옵션 값 조합을 항목 코드 · 값 ID · 값으로 담는다")
    void carryOptionValuesWithCodes() {
        withOptionsAndSizeSpecs110And120();
        ReflectionTestUtils.setField(clothing.navy(), "id", NAVY_ID);
        ReflectionTestUtils.setField(clothing.size110(), "id", SIZE_110_ID);
        ReflectionTestUtils.setField(clothing.size120(), "id", SIZE_120_ID);

        ProductChangedEvent.Data data = ProductChangedData.of(product, top,
            Map.of(OPTION_110_ID, 10, OPTION_120_ID, 10));

        assertThat(data.options()).extracting(ProductChangedEvent.Option::values).containsExactly(
            List.of(new ProductChangedEvent.AttributeEntry("COLOR", NAVY_ID, "네이비"),
                new ProductChangedEvent.AttributeEntry("SIZE", SIZE_110_ID, "110")),
            List.of(new ProductChangedEvent.AttributeEntry("COLOR", NAVY_ID, "네이비"),
                new ProductChangedEvent.AttributeEntry("SIZE", SIZE_120_ID, "120")));
    }

    @Test
    @DisplayName("SALE-08 상품 정보 값을 항목 코드 · 값 ID · 값으로 담는다. 다중 선택은 값마다 하나씩, 직접 입력은 값 ID 없이 입력한 글로 담는다")
    void carryInfoValuesWithCodes() {
        ProductFixture.Clothing info = ProductFixture.clothing();
        ReflectionTestUtils.setField(info.cotton(), "id", COTTON_ID);
        ReflectionTestUtils.setField(info.brushed(), "id", BRUSHED_ID);
        ReflectionTestUtils.setField(info.stretch(), "id", STRETCH_ID);
        Product withInfoValues = Product.register(ProductFixture.productAWith(info.type(), List.of(
            new Product.InfoValueInfo(info.mainMaterial(), List.of(info.cotton()), null),
            new Product.InfoValueInfo(info.fabricComposition(), List.of(), "면 95%, 폴리우레탄 5%"),
            new Product.InfoValueInfo(info.feature(), List.of(info.brushed(), info.stretch()), null))));

        Category infoTop = CategoryFixture.top(CategoryFixture.clothing(), info.type());

        ProductChangedEvent.Data data = ProductChangedData.of(withInfoValues, infoTop, Map.of());

        assertThat(data.infoValues()).containsExactlyInAnyOrder(
            new ProductChangedEvent.AttributeEntry("MAIN_MATERIAL", COTTON_ID, "면"),
            new ProductChangedEvent.AttributeEntry("FABRIC_COMPOSITION", null, "면 95%, 폴리우레탄 5%"),
            new ProductChangedEvent.AttributeEntry("FEATURE", BRUSHED_ID, "기모"),
            new ProductChangedEvent.AttributeEntry("FEATURE", STRETCH_ID, "신축성"));
    }

    @Test
    @DisplayName("SALE-08 상품의 브랜드와 설명을 담는다")
    void carryBrandAndDescription() {
        ProductChangedEvent.Data data = ProductChangedData.of(product, top, Map.of());

        assertThat(data.brand()).isEqualTo("키들리");
        assertThat(data.description()).isEqualTo("상품 설명");
    }

    @Test
    @DisplayName("SALE-08 실측 행마다 사이즈 · 권장 키 · 실측 값을 상품에 저장된 순서대로 담는다")
    void carrySizeSpecsInStoredOrder() {
        withOptionsAndSizeSpecs110And120();

        ProductChangedEvent.Data data = ProductChangedData.of(product, top, Map.of());

        assertThat(data.sizeSpecs()).containsExactly(
            new ProductChangedEvent.SizeSpec("110", 105, 115, top110Measurements()),
            new ProductChangedEvent.SizeSpec("120", 115, 125, top120Measurements()));
    }

    @Test
    @DisplayName("SIZE-05 사이즈 무관 상품은 FREE 행을 권장 키 없이 실측 값과 함께 담는다")
    void carryFreeSizeSpecOfSizeFreeProduct() {
        product.addOptions(addOptions(List.of(option(clothing.navy(), clothing.sizeFree()))), clothing.type());
        product.addSizeSpecs(addSizeSpecs(List.of(
            spec(clothing.sizeFree(), null, null, top110Measurements()))), top, clothing.type());
        ReflectionTestUtils.setField(product.getOptions().get(0), "id", OPTION_110_ID);

        ProductChangedEvent.Data data = ProductChangedData.of(product, top, Map.of());

        assertThat(data.sizeSpecs()).containsExactly(
            new ProductChangedEvent.SizeSpec("FREE", null, null, top110Measurements()));
    }

    @Test
    @DisplayName("SIZE-06 실측 행이 없으면 사이즈별 실측을 빈 목록으로 옮긴다")
    void carryEmptySizeSpecsWithoutSizeSpecRows() {
        // 실측 행이 없는 실제 경우는 사이즈 역할 항목이 없는 유형의 상품인데, 그런 유형의 픽스처가 없어 옮기는 동작만 확인한다
        product.addOptions(addOptions(List.of(option(clothing.navy(), clothing.size110()))), clothing.type());
        ReflectionTestUtils.setField(product.getOptions().get(0), "id", OPTION_110_ID);

        ProductChangedEvent.Data data = ProductChangedData.of(product, top, Map.of());

        assertThat(data.sizeSpecs()).isEmpty();
    }

    @Test
    @DisplayName("SALE-02 보관한 옵션은 담지 않고, 판매중지 옵션은 판매중지로 담는다")
    void skipArchivedAndCarryStoppedOptions() {
        withOptionsAndSizeSpecs110And120();
        product.startSale(SELLER_ID, top, clothing.type());
        // 옵션 상태를 바꾸는 업무 메서드가 아직 없어 칸을 직접 바꾼다
        ReflectionTestUtils.setField(product.getOptions().get(0), "saleStatus", ProductOptionStatus.ARCHIVED);
        ReflectionTestUtils.setField(product.getOptions().get(1), "saleStatus", ProductOptionStatus.STOPPED);

        ProductChangedEvent.Data data = ProductChangedData.of(product, top,
            Map.of(OPTION_110_ID, 10, OPTION_120_ID, 10));

        assertThat(data.options())
            .extracting(ProductChangedEvent.Option::optionId, ProductChangedEvent.Option::saleStatus)
            .containsExactly(tuple(OPTION_120_ID, OptionSaleStatus.STOPPED));
    }

    @Test
    @DisplayName("SIZE-02 사이즈가 모두 FREE인 상품은 사이즈 무관으로 담고, 권장 키 없는 옵션의 권장 키는 비워 두며 재고 행이 없으면 품절로 담는다")
    void carrySizeFreeProduct() {
        product.addOptions(addOptions(List.of(
            option(clothing.navy(), clothing.sizeFree()),
            option(clothing.oatmeal(), clothing.sizeFree()))), clothing.type());
        product.addSizeSpecs(addSizeSpecs(List.of(
            spec(clothing.sizeFree(), null, null, top110Measurements()))), top, clothing.type());
        ReflectionTestUtils.setField(product.getOptions().get(0), "id", OPTION_110_ID);
        ReflectionTestUtils.setField(product.getOptions().get(1), "id", OPTION_120_ID);

        ProductChangedEvent.Data data = ProductChangedData.of(product, top, Map.of());

        assertThat(data.sizeFree()).isTrue();
        // 판매 가능 수량을 하나도 넘기지 않았으므로, 재고 행이 없는 옵션은 살 수 없어 둘 다 품절이다
        assertThat(data.options())
            .extracting(ProductChangedEvent.Option::size, ProductChangedEvent.Option::minHeightCm,
                ProductChangedEvent.Option::maxHeightCm, ProductChangedEvent.Option::soldOut)
            .containsExactly(tuple("FREE", null, null, true), tuple("FREE", null, null, true));
    }

    @Test
    @DisplayName("SALE-08 카테고리 경로를 대분류부터 종류까지 ID · 이름으로 담고, 마지막 ID는 상품의 카테고리 ID와 같다")
    void carryCategoryPathFromTopmostToProductCategory() {
        ReflectionTestUtils.setField(top.getParent(), "id", CLOTHING_CATEGORY_ID);
        ReflectionTestUtils.setField(top, "id", CATEGORY_ID);

        ProductChangedEvent.Data data = ProductChangedData.of(product, top, Map.of());

        assertThat(data.categoryPath()).containsExactly(
            new ProductChangedEvent.CategoryPathEntry(CLOTHING_CATEGORY_ID, "의류"),
            new ProductChangedEvent.CategoryPathEntry(CATEGORY_ID, "상의"));
    }

    // 상품 A에 네이비 110 · 120 옵션과 두 사이즈의 실측 · 권장 키를 넣고, 옵션 ID를 정해 둔다
    private void withOptionsAndSizeSpecs110And120() {
        product.addOptions(addOptions(List.of(
            option(clothing.navy(), clothing.size110()),
            option(clothing.navy(), clothing.size120()))), clothing.type());
        product.addSizeSpecs(addSizeSpecs(List.of(
            spec(clothing.size110(), 105, 115, top110Measurements()),
            spec(clothing.size120(), 115, 125, top120Measurements()))), top, clothing.type());
        ReflectionTestUtils.setField(product.getOptions().get(0), "id", OPTION_110_ID);
        ReflectionTestUtils.setField(product.getOptions().get(1), "id", OPTION_120_ID);
    }
}
