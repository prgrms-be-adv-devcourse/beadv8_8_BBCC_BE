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
    private static final long OPTION_110_ID = 11L;
    private static final long OPTION_120_ID = 12L;
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

        ProductChangedEvent.Data data = ProductChangedData.of(product, clothing.type(),
            Map.of(OPTION_110_ID, 10, OPTION_120_ID, 0));

        assertThat(data).isEqualTo(new ProductChangedEvent.Data(
            PRODUCT_ID, SELLER_ID, "상품 A", CATEGORY_ID, ProductStatus.ON_SALE, product.getThumbnailUrl(),
            REGISTERED_AT, false, List.of(
                new ProductChangedEvent.Option(OPTION_110_ID, "색상: 네이비 / 사이즈: 110", "110", CLOTHING_PRICE,
                    105, 115, false, OptionSaleStatus.ON_SALE),
                new ProductChangedEvent.Option(OPTION_120_ID, "색상: 네이비 / 사이즈: 120", "120", CLOTHING_PRICE,
                    115, 125, true, OptionSaleStatus.ON_SALE))));
    }

    @Test
    @DisplayName("SALE-02 보관한 옵션은 담지 않고, 판매중지 옵션은 판매중지로 담는다")
    void skipArchivedAndCarryStoppedOptions() {
        withOptionsAndSizeSpecs110And120();
        product.startSale(SELLER_ID, top, clothing.type());
        // 옵션 상태를 바꾸는 업무 메서드가 아직 없어 칸을 직접 바꾼다
        ReflectionTestUtils.setField(product.getOptions().get(0), "saleStatus", ProductOptionStatus.ARCHIVED);
        ReflectionTestUtils.setField(product.getOptions().get(1), "saleStatus", ProductOptionStatus.STOPPED);

        ProductChangedEvent.Data data = ProductChangedData.of(product, clothing.type(),
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

        ProductChangedEvent.Data data = ProductChangedData.of(product, clothing.type(), Map.of());

        assertThat(data.sizeFree()).isTrue();
        // 판매 가능 수량을 하나도 넘기지 않았으므로, 재고 행이 없는 옵션은 살 수 없어 둘 다 품절이다
        assertThat(data.options())
            .extracting(ProductChangedEvent.Option::size, ProductChangedEvent.Option::minHeightCm,
                ProductChangedEvent.Option::maxHeightCm, ProductChangedEvent.Option::soldOut)
            .containsExactly(tuple("FREE", null, null, true), tuple("FREE", null, null, true));
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
