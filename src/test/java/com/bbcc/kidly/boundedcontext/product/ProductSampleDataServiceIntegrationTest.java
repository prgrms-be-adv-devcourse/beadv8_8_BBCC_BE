package com.bbcc.kidly.boundedcontext.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.tuple;

import com.bbcc.kidly.boundedcontext.member.app.MemberInitialDataService;
import com.bbcc.kidly.boundedcontext.product.app.port.search.ProductSearchItem;
import com.bbcc.kidly.boundedcontext.product.app.product.ProductDetailService;
import com.bbcc.kidly.boundedcontext.product.app.product.ProductSampleDataService;
import com.bbcc.kidly.boundedcontext.product.app.product.SellerProductService;
import com.bbcc.kidly.boundedcontext.product.app.product.dto.ProductDetailResult;
import com.bbcc.kidly.boundedcontext.product.app.product.dto.SellerProductDetailResult;
import com.bbcc.kidly.boundedcontext.product.app.search.ProductListService;
import com.bbcc.kidly.boundedcontext.product.app.search.dto.ProductListRequest;
import com.bbcc.kidly.boundedcontext.product.app.type.ProductReferenceDataService;
import com.bbcc.kidly.boundedcontext.seller.app.SellerInitialDataService;
import com.bbcc.kidly.support.IntegrationTest;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class ProductSampleDataServiceIntegrationTest {

    private static final Long SELLER_ID = 2L;

    @Autowired
    private MemberInitialDataService memberInitialDataService;

    @Autowired
    private SellerInitialDataService sellerInitialDataService;

    @Autowired
    private ProductReferenceDataService productReferenceDataService;

    @Autowired
    private ProductSampleDataService productSampleDataService;

    @Autowired
    private ProductListService productListService;

    @Autowired
    private ProductDetailService productDetailService;

    @Autowired
    private SellerProductService sellerProductService;

    // 앱 시작 때와 같은 순서로 회원 · 판매자 초기 데이터를 먼저 넣는다. 판매자 2의 상품 복사본이 있어야 목록에 상점 이름이 나온다
    @BeforeEach
    void setUp() {
        memberInitialDataService.load();
        sellerInitialDataService.load();
    }

    @Test
    @DisplayName("판매자 2의 샘플 상품 두 개를 판매 중으로 넣고, 판매 시작 이벤트로 목록에 나온다")
    void load() {
        productReferenceDataService.load();

        productSampleDataService.load();

        List<ProductSearchItem> items = productListService.list(
            new ProductListRequest(null, null, null, "PRICE_ASC", null, null)).getContent();
        assertThat(items)
            .extracting(ProductSearchItem::name, ProductSearchItem::businessName, ProductSearchItem::minPrice)
            .containsExactly(
                tuple("네이비 데일리 맨투맨", "테스트상점", 25_000L),
                tuple("그레이 조거 팬츠", "테스트상점", 30_000L));

        Long topId = items.get(0).productId();
        ProductDetailResult top = productDetailService.findDetail(topId);
        assertThat(top.brand()).isEqualTo("BBCC");
        assertThat(top.status()).isEqualTo("ON_SALE");
        assertThat(top.options()).extracting(ProductDetailResult.Option::optionName, ProductDetailResult.Option::price)
            .containsExactly(
                tuple("색상: 네이비 / 사이즈: 110", 25_000L),
                tuple("색상: 네이비 / 사이즈: 120", 25_000L));
        assertThat(top.productInfo()).contains(new ProductDetailResult.Info("MAIN_MATERIAL", "주요소재", "면"));
        assertThat(top.sizeSpecs())
            .extracting(ProductDetailResult.SizeSpec::size, ProductDetailResult.SizeSpec::minHeightCm,
                ProductDetailResult.SizeSpec::maxHeightCm, spec -> centimeters(spec.measurements()))
            .containsExactly(
                tuple("110", 105, 115,
                    Map.of("TOTAL_LENGTH", 42, "SHOULDER_WIDTH", 30, "CHEST_WIDTH", 34, "SLEEVE_LENGTH", 38)),
                tuple("120", 115, 125,
                    Map.of("TOTAL_LENGTH", 45, "SHOULDER_WIDTH", 32, "CHEST_WIDTH", 36, "SLEEVE_LENGTH", 41)));
        assertThat(availableQuantities(topId)).containsExactly(10, 10);

        Long bottomId = items.get(1).productId();
        ProductDetailResult bottom = productDetailService.findDetail(bottomId);
        assertThat(bottom.brand()).isEqualTo("BBCC");
        assertThat(bottom.options()).extracting(ProductDetailResult.Option::optionName)
            .containsExactly("색상: 그레이 / 사이즈: 120");
        assertThat(bottom.productInfo()).contains(new ProductDetailResult.Info("MAIN_MATERIAL", "주요소재", "면"));
        assertThat(bottom.sizeSpecs())
            .extracting(ProductDetailResult.SizeSpec::size, ProductDetailResult.SizeSpec::minHeightCm,
                ProductDetailResult.SizeSpec::maxHeightCm, spec -> centimeters(spec.measurements()))
            .containsExactly(tuple("120", 115, 125,
                Map.of("TOTAL_LENGTH", 66, "WAIST_WIDTH", 25, "HIP_WIDTH", 34, "THIGH_WIDTH", 20, "RISE", 21)));
        assertThat(availableQuantities(bottomId)).containsExactly(5);
    }

    @Test
    @DisplayName("상품이 하나라도 있으면 샘플 상품을 넣지 않는다")
    void skipWhenProductExists() {
        productReferenceDataService.load();
        productSampleDataService.load();

        productSampleDataService.load();

        assertThat(productListService.list(new ProductListRequest(null, null, null, null, null, null)).getContent())
            .hasSize(2);
    }

    @Test
    @DisplayName("기준 데이터에 상의 · 하의가 없으면 샘플 상품을 넣지 않고 앱 시작을 막지 않는다")
    void skipWhenReferenceDataMissing() {
        assertThatCode(() -> productSampleDataService.load()).doesNotThrowAnyException();

        assertThat(productListService.list(new ProductListRequest(null, null, null, null, null, null)).getContent())
            .isEmpty();
    }

    private List<Integer> availableQuantities(Long productId) {
        return sellerProductService.findMyProductDetail(SELLER_ID, productId).options().stream()
            .map(SellerProductDetailResult.Option::availableQuantity)
            .toList();
    }

    // JSONB에서 읽은 값은 소수 자릿수가 저장할 때와 다를 수 있어 정수 cm로 바꿔 비교한다
    private Map<String, Integer> centimeters(Map<String, BigDecimal> measurements) {
        return measurements.entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().intValueExact()));
    }
}
