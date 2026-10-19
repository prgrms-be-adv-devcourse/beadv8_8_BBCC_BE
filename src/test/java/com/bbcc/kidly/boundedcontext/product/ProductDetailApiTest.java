package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProductFixture.SELLER_ID;
import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.addOptions;
import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.option;
import static com.bbcc.kidly.boundedcontext.product.SizeSpecFixture.addSizeSpecs;
import static com.bbcc.kidly.boundedcontext.product.SizeSpecFixture.spec;
import static com.bbcc.kidly.boundedcontext.product.SizeSpecFixture.top110Measurements;
import static com.bbcc.kidly.boundedcontext.product.SizeSpecFixture.top120Measurements;

import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryOpening;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryRepository;
import com.bbcc.kidly.boundedcontext.product.domain.product.Product;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductRepository;
import com.bbcc.kidly.boundedcontext.product.domain.replica.ProductReplicaSeller;
import com.bbcc.kidly.boundedcontext.product.domain.replica.ProductReplicaSellerRepository;
import com.bbcc.kidly.boundedcontext.product.domain.type.Attribute;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeInputType;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeRole;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeValue;
import com.bbcc.kidly.boundedcontext.product.domain.type.Category;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductType;
import com.bbcc.kidly.support.FunctionalTest;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 구매자가 보는 상품 상세 API를 HTTP 요청부터 응답까지 확인한다.
 * 항목 · 목록 값 · 상품 유형 · 카테고리 · 실측 항목은 저장할 리포지토리가 없어 EntityManager로 저장하고,
 * 상품은 상품 리포지토리로, 재고는 재고 리포지토리로 저장한다.
 */
@FunctionalTest
class ProductDetailApiTest {

    // 색상 · 사이즈는 필수 옵션 항목, 주요소재는 필수 정보 항목, 혼용률은 선택 직접 입력 항목, 특징은 선택 다중 선택 항목이다
    private final Attribute color = ProductTypeFixture.color();
    private final Attribute size = ProductTypeFixture.size();
    private final Attribute mainMaterial = ProductTypeFixture.mainMaterial();
    private final Attribute fabricComposition = ProductTypeFixture.fabricComposition();
    private final Attribute feature = Attribute.create("특징", "FEATURE", AttributeInputType.MULTI_LIST);
    private final AttributeValue navy = AttributeValue.create(color, "네이비", "#2f3e5c");
    private final AttributeValue oatmeal = AttributeValue.create(color, "오트밀", "#e3d9c6");
    private final AttributeValue size110 = AttributeValue.create(size, "110", null);
    private final AttributeValue size120 = AttributeValue.create(size, "120", null);
    private final AttributeValue cotton = AttributeValue.create(mainMaterial, "면", null);
    private final AttributeValue brushed = AttributeValue.create(feature, "기모", null);
    private final AttributeValue stretch = AttributeValue.create(feature, "신축성", null);
    private final ProductType clothingType = ProductType.define("의류", List.of(
        ProductTypeFixture.requiredOption(color),
        ProductTypeFixture.sizeOption(size),
        ProductTypeFixture.requiredInfo(mainMaterial),
        ProductTypeFixture.optionalTextInfo(fabricComposition),
        new ProductType.LinkInfo(feature, AttributeRole.INFO, false, true, false, false, List.of())));

    @Autowired
    private RestTestClient client;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private ProductReplicaSellerRepository productReplicaSellerRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("판매중 상품의 상세는 로그인 없이 받고, 설명과 옵션 · 상품정보 표 · 실측을 정해진 순서로 담는다")
    void getDetailOfProductOnSale() {
        Product product = savedProductOnSale(10, 10, 10);

        client.get().uri("/api/v1/products/{productId}", product.getId())
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.productId").isEqualTo(String.valueOf(product.getId()))
            .jsonPath("$.name").isEqualTo("상품 A")
            .jsonPath("$.brand").isEqualTo("키들리")
            .jsonPath("$.categoryId").isEqualTo(String.valueOf(product.getCategoryId()))
            .jsonPath("$.description").isEqualTo("상품 설명")
            .jsonPath("$.status").isEqualTo("ON_SALE")
            .jsonPath("$.sizeFree").isEqualTo(false)
            .jsonPath("$.minPrice").isEqualTo(25_000)
            .jsonPath("$.imageUrls").isEqualTo(List.of("https://…/a-1.jpg"))
            // 항목은 유형에 연결한 순서이고, 값은 옵션에 쓴 값만 값 ID 순서로 담는다. 오트밀을 네이비보다 먼저 저장했다
            .jsonPath("$.optionAttributes[*].code").isEqualTo(List.of("COLOR", "SIZE"))
            .jsonPath("$.optionAttributes[0].attributeId").isEqualTo(String.valueOf(color.getId()))
            .jsonPath("$.optionAttributes[0].name").isEqualTo("색상")
            .jsonPath("$.optionAttributes[0].values[*].value").isEqualTo(List.of("오트밀", "네이비"))
            .jsonPath("$.optionAttributes[0].values[1].valueId").isEqualTo(String.valueOf(navy.getId()))
            .jsonPath("$.optionAttributes[0].values[1].colorCode").isEqualTo("#2f3e5c")
            .jsonPath("$.optionAttributes[1].values[*].value").isEqualTo(List.of("110", "120"))
            .jsonPath("$.optionAttributes[1].values[0].colorCode").hasJsonPath()
            .jsonPath("$.optionAttributes[1].values[0].colorCode").isEqualTo(null)
            .jsonPath("$.options[*].optionName").isEqualTo(List.of(
                "색상: 네이비 / 사이즈: 110", "색상: 네이비 / 사이즈: 120", "색상: 오트밀 / 사이즈: 110"))
            .jsonPath("$.options[0].optionId").isEqualTo(String.valueOf(product.getOptions().get(0).getId()))
            .jsonPath("$.options[0].valueIds")
            .isEqualTo(List.of(String.valueOf(navy.getId()), String.valueOf(size110.getId())))
            .jsonPath("$.options[0].price").isEqualTo(25_000)
            .jsonPath("$.options[0].purchasable").isEqualTo(true)
            .jsonPath("$.options[0].stockStatus").isEqualTo("AVAILABLE")
            .jsonPath("$.options[0].stockQuantity").hasJsonPath()
            .jsonPath("$.options[0].stockQuantity").isEqualTo(null)
            // 상품정보 표는 유형에 연결한 정보 항목 순서이고, 입력하지 않은 선택 항목은 null, 다중 선택 값은 이어 붙인다
            .jsonPath("$.productInfo[*].code").isEqualTo(List.of("MAIN_MATERIAL", "FABRIC_COMPOSITION", "FEATURE"))
            .jsonPath("$.productInfo[0].name").isEqualTo("주요소재")
            .jsonPath("$.productInfo[0].value").isEqualTo("면")
            .jsonPath("$.productInfo[1].value").hasJsonPath()
            .jsonPath("$.productInfo[1].value").isEqualTo(null)
            .jsonPath("$.productInfo[2].value").isEqualTo("기모, 신축성")
            .jsonPath("$.sizeSpecs[*].size").isEqualTo(List.of("110", "120"))
            .jsonPath("$.sizeSpecs[0].minHeightCm").isEqualTo(105)
            .jsonPath("$.sizeSpecs[0].maxHeightCm").isEqualTo(115)
            .jsonPath("$.sizeSpecs[0].measurements.TOTAL_LENGTH").isEqualTo(42)
            .jsonPath("$.sizeSpecs[1].measurements.SLEEVE_LENGTH").isEqualTo(41)
            .jsonPath("$.measurementItems[*].code")
            .isEqualTo(List.of("TOTAL_LENGTH", "SHOULDER_WIDTH", "CHEST_WIDTH", "SLEEVE_LENGTH"))
            .jsonPath("$.measurementItems[0].name").isEqualTo("총장")
            .jsonPath("$.measurementItems[0].unit").isEqualTo("cm");
    }

    @Test
    @DisplayName("판매자가 설명을 입력하지 않은 상품은 설명 칸을 빼지 않고 null로 담는다")
    void descriptionIsNullWhenSellerLeftItEmpty() {
        Product product = savedProductOnSale(10, 10, 10);
        // 상품 A 등록 정보에는 설명이 있어서, 설명 없이 등록한 상품을 만들려고 설명 칸을 비운다
        jdbcTemplate.update("update product.product set description = null where id = ?", product.getId());

        client.get().uri("/api/v1/products/{productId}", product.getId())
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.description").hasJsonPath()
            .jsonPath("$.description").isEqualTo(null);
    }

    @Test
    @DisplayName("판매자 복사본이 있는 판매자의 상품은 상세에 판매자 상호명을 담는다")
    void businessNameFromReplicaSeller() {
        Product product = savedProductOnSale(10, 10, 10);
        // 상품 A의 판매자 2가 승인되어 판매자 정보 변경됨을 받은 상태를 만든다
        inTransaction(() -> productReplicaSellerRepository.save(
            ProductReplicaSeller.create(ProductReplicaSellerFixture.sellerApproved())));

        client.get().uri("/api/v1/products/{productId}", product.getId())
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.businessName").isEqualTo("테스트상점");
    }

    @Test
    @DisplayName("SALE-09 판매 가능 수량이 기준 수 5보다 많으면 수량 없이 구매 가능, 5 이하이면 수량, 0이면 품절이다")
    void stockDisplayByAvailableQuantity() {
        Product product = savedProductOnSale(8, 3, 0);

        client.get().uri("/api/v1/products/{productId}", product.getId())
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.options[*].stockStatus").isEqualTo(List.of("AVAILABLE", "LOW", "SOLD_OUT"))
            .jsonPath("$.options[0].stockQuantity").isEqualTo(null)
            .jsonPath("$.options[1].stockQuantity").isEqualTo(3)
            .jsonPath("$.options[2].stockQuantity").isEqualTo(null)
            .jsonPath("$.options[*].purchasable").isEqualTo(List.of(true, true, false));
    }

    @Test
    @DisplayName("SALE-04 · SALE-06 판매중지 옵션은 재고가 있어도 살 수 없고, 같은 상품의 판매중 옵션은 살 수 있다")
    void stoppedOptionIsNotPurchasable() {
        Product product = savedProductOnSale(10, 10, 10);
        // 옵션을 판매중지하는 업무 메서드가 아직 없어서 상태 칸을 직접 바꾼다
        changeOptionStatus(product.getOptions().get(1).getId(), "STOPPED");

        client.get().uri("/api/v1/products/{productId}", product.getId())
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.options[*].purchasable").isEqualTo(List.of(true, false, true));
    }

    @Test
    @DisplayName("SALE-06 판매중지 상품도 상세는 받지만 상태가 STOPPED이고 옵션은 모두 살 수 없다")
    void stoppedProductShowsDetailButNothingPurchasable() {
        Product product = savedProductOnSale(10, 10, 10);
        // 상품을 판매중지하는 업무 메서드가 아직 없어서 상태 칸을 직접 바꾼다
        jdbcTemplate.update("update product.product set status = 'STOPPED' where id = ?", product.getId());

        client.get().uri("/api/v1/products/{productId}", product.getId())
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.status").isEqualTo("STOPPED")
            .jsonPath("$.options[*].purchasable").isEqualTo(List.of(false, false, false));
    }

    @Test
    @DisplayName("SALE-06 보관 옵션은 옵션 목록에도, 옵션 항목의 값에도 없다")
    void archivedOptionIsHidden() {
        Product product = savedProductOnSale(10, 10, 10);
        // 옵션을 보관하는 업무 메서드가 아직 없어서 상태 칸을 직접 바꾼다. 오트밀은 보관한 옵션에만 쓴 값이다
        changeOptionStatus(product.getOptions().get(2).getId(), "ARCHIVED");

        client.get().uri("/api/v1/products/{productId}", product.getId())
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.options[*].optionName").isEqualTo(List.of(
                "색상: 네이비 / 사이즈: 110", "색상: 네이비 / 사이즈: 120"))
            .jsonPath("$.optionAttributes[0].values[*].value").isEqualTo(List.of("네이비"));
    }

    @Test
    @DisplayName("없는 상품의 상세를 찾으면 404다")
    void missingProductIsNotFound() {
        expectProblem("/api/v1/products/999999", HttpStatus.NOT_FOUND,
            "상품을 찾을 수 없습니다", "상품을 다시 확인해 주세요.");
    }

    @Test
    @DisplayName("판매를 시작하기 전인 판매대기 상품의 상세를 찾으면 없는 상품과 같이 404다")
    void productBeforeSaleIsNotFound() {
        Product product = savedProductOnSale(10, 10, 10);
        // 판매 시작 전 상품을 상세 조회하는 경우를 만들려고 상태 칸을 판매대기로 되돌린다
        jdbcTemplate.update("update product.product set status = 'SALE_PENDING' where id = ?", product.getId());

        expectProblem("/api/v1/products/" + product.getId(), HttpStatus.NOT_FOUND,
            "상품을 찾을 수 없습니다", "상품을 다시 확인해 주세요.");
    }

    @Test
    @DisplayName("상품 ID가 숫자가 아니면 400이다")
    void nonNumericProductIdIsBadRequest() {
        client.get().uri("/api/v1/products/abc")
            .exchange()
            .expectStatus().isBadRequest()
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    @DisplayName("로그인 없이 부르는 API여도 잘못된 토큰을 보내면 401이다")
    void invalidTokenIsUnauthorized() {
        Product product = savedProductOnSale(10, 10, 10);

        client.get().uri("/api/v1/products/{productId}", product.getId())
            .headers(headers -> headers.setBearerAuth("invalid-token"))
            .exchange()
            .expectStatus().isUnauthorized();
    }

    // 실패 응답이 ProblemDetail 5필드(type, title, status, detail, instance)인지 본다
    private void expectProblem(String path, HttpStatus status, String title, String detail) {
        client.get().uri(path)
            .exchange()
            .expectStatus().isEqualTo(status)
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.type").exists()
            .jsonPath("$.title").isEqualTo(title)
            .jsonPath("$.status").isEqualTo(status.value())
            .jsonPath("$.detail").isEqualTo(detail)
            .jsonPath("$.instance").isEqualTo(path);
    }

    // 상품 A에 네이비 110 · 네이비 120 · 오트밀 110 옵션과 110 · 120 실측을 넣어 판매를 시작하고 저장한다.
    // quantities는 옵션 순서대로의 판매 가능 수량이다. 판매자가 입력한 정보 값은 주요소재 면, 특징 기모 · 신축성이다
    private Product savedProductOnSale(int... quantities) {
        Product.RegisterInfo info = ProductFixture.productAWith(clothingType, List.of(
            new Product.InfoValueInfo(mainMaterial, List.of(cotton), null),
            new Product.InfoValueInfo(feature, List.of(brushed, stretch), null)));
        Category category = info.category();
        Product product = Product.register(info);
        product.addOptions(addOptions(List.of(
            option(navy, size110), option(navy, size120), option(oatmeal, size110))), clothingType);
        product.addSizeSpecs(addSizeSpecs(List.of(
            spec(size110, 105, 115, top110Measurements()),
            spec(size120, 115, 125, top120Measurements()))), category, clothingType);
        product.startSale(SELLER_ID, category, clothingType);
        inTransaction(() -> {
            saveClothingType();
            saveCategory(category);
            productRepository.save(product);
            for (int i = 0; i < quantities.length; i++) {
                inventoryRepository.saveOpening(
                    InventoryOpening.open(product.getOptions().get(i), quantities[i], SELLER_ID));
            }
            return null;
        });
        return product;
    }

    // 항목과 목록 값은 상품 유형 저장에 cascade되지 않아 먼저 저장한다. 오트밀은 네이비보다 먼저 저장해 값 ID가 더 작다
    private void saveClothingType() {
        Stream.of(color, size, mainMaterial, fabricComposition, feature).forEach(entityManager::persist);
        Stream.of(oatmeal, navy, size110, size120, cotton, brushed, stretch).forEach(entityManager::persist);
        entityManager.persist(clothingType);
    }

    private void changeOptionStatus(Long optionId, String status) {
        jdbcTemplate.update("update product.product_option set sale_status = ? where id = ?", status, optionId);
    }

    private void saveCategory(Category category) {
        entityManager.persist(category.getParent());
        category.getMeasurementItems().forEach(link -> entityManager.persist(link.getMeasurementItem()));
        entityManager.persist(category);
    }

    private <T> T inTransaction(Supplier<T> work) {
        return new TransactionTemplate(transactionManager).execute(status -> work.get());
    }
}
