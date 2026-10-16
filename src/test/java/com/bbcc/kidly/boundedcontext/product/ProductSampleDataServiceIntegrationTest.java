package com.bbcc.kidly.boundedcontext.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.tuple;

import com.bbcc.kidly.boundedcontext.childprofile.app.ChildProfileInitialDataService;
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
import com.bbcc.kidly.boundedcontext.product.domain.type.Attribute;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeRepository;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeValue;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeValueRepository;
import com.bbcc.kidly.boundedcontext.product.domain.type.Category;
import com.bbcc.kidly.boundedcontext.product.domain.type.CategoryRepository;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductType;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductTypeRepository;
import com.bbcc.kidly.boundedcontext.recommend.app.recommended.RecommendedProductService;
import com.bbcc.kidly.boundedcontext.recommend.app.recommended.dto.RecommendedProductResult;
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
    private ChildProfileInitialDataService childProfileInitialDataService;

    @Autowired
    private RecommendedProductService recommendedProductService;

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

    @Autowired
    private AttributeRepository attributeRepository;

    @Autowired
    private AttributeValueRepository attributeValueRepository;

    @Autowired
    private ProductTypeRepository productTypeRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    // 앱 시작 때와 같은 순서로 회원 초기 데이터를 먼저 넣는다. 판매자 초기 데이터는 판매자가 없는 경우를 보려고 테스트마다 넣는다
    @BeforeEach
    void setUp() {
        memberInitialDataService.load();
    }

    @Test
    @DisplayName("판매자 2의 샘플 상품 두 개를 판매 중으로 넣고, 판매 시작 이벤트로 목록과 자녀 맞춤 목록에 나온다")
    void load() {
        childProfileInitialDataService.load();
        sellerInitialDataService.load();
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

        // 보호자(회원 1)의 자녀 1은 키 115cm라 두 상품 모두 120을 추천받는다
        assertThat(recommendedProductService.list(1L, 1L, null, 0, 20).getContent())
            .extracting(RecommendedProductResult::name, RecommendedProductResult::recommendedSize)
            .containsExactlyInAnyOrder(tuple("네이비 데일리 맨투맨", "120"), tuple("그레이 조거 팬츠", "120"));
    }

    @Test
    @DisplayName("상품이 하나라도 있으면 샘플 상품을 넣지 않는다")
    void skipWhenProductExists() {
        sellerInitialDataService.load();
        productReferenceDataService.load();
        productSampleDataService.load();

        productSampleDataService.load();

        assertThat(productListService.list(new ProductListRequest(null, null, null, null, null, null)).getContent())
            .hasSize(2);
    }

    @Test
    @DisplayName("기준 데이터에 상의 · 하의가 없으면 샘플 상품을 넣지 않고 앱 시작을 막지 않는다")
    void skipWhenReferenceDataMissing() {
        sellerInitialDataService.load();

        assertThatCode(() -> productSampleDataService.load()).doesNotThrowAnyException();

        assertThat(productListService.list(new ProductListRequest(null, null, null, null, null, null)).getContent())
            .isEmpty();
    }

    @Test
    @DisplayName("판매자 2의 판매자 복사본이 없으면 샘플 상품을 넣지 않고 앱 시작을 막지 않는다")
    void skipWhenSellerMissing() {
        productReferenceDataService.load();

        assertThatCode(() -> productSampleDataService.load()).doesNotThrowAnyException();

        assertThat(productListService.list(new ProductListRequest(null, null, null, null, null, null)).getContent())
            .isEmpty();
    }

    @Test
    @DisplayName("기준 데이터에 샘플 상품이 쓰는 값이 없으면 샘플 상품을 넣지 않고 앱 시작을 막지 않는다")
    void skipWhenValueMissing() {
        sellerInitialDataService.load();
        saveReferenceDataWithoutNavy();

        assertThatCode(() -> productSampleDataService.load()).doesNotThrowAnyException();

        assertThat(productListService.list(new ProductListRequest(null, null, null, null, null, null)).getContent())
            .isEmpty();
    }

    // 의류 > 상의 · 하의는 있지만 색상 값에 네이비가 없는 기준 데이터다
    private void saveReferenceDataWithoutNavy() {
        Attribute color = attributeRepository.save(ProductTypeFixture.color());
        Attribute size = attributeRepository.save(ProductTypeFixture.size());
        Attribute material = attributeRepository.save(ProductTypeFixture.mainMaterial());
        attributeValueRepository.save(AttributeValue.create(color, "그레이", "#9e9e9e"));
        attributeValueRepository.save(AttributeValue.create(size, "110", null));
        attributeValueRepository.save(AttributeValue.create(size, "120", null));
        attributeValueRepository.save(AttributeValue.create(material, "면", null));
        ProductType clothingType = productTypeRepository.save(ProductType.define("의류", List.of(
            ProductTypeFixture.requiredOption(color),
            ProductTypeFixture.sizeOption(size),
            ProductTypeFixture.requiredInfo(material))));
        Category clothingCategory = categoryRepository.save(Category.createMiddle("의류", null));
        categoryRepository.save(Category.createLeaf(
            new Category.LeafInfo("상의", clothingCategory, clothingType, List.of())));
        categoryRepository.save(Category.createLeaf(
            new Category.LeafInfo("하의", clothingCategory, clothingType, List.of())));
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
