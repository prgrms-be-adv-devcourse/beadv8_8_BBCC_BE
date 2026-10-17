package com.bbcc.kidly.boundedcontext.product;

import com.bbcc.kidly.boundedcontext.product.domain.product.Product;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductRepository;
import com.bbcc.kidly.boundedcontext.product.domain.type.Attribute;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeInputType;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeValue;
import com.bbcc.kidly.boundedcontext.product.domain.type.Category;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductType;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductTypeAttribute;
import com.bbcc.kidly.support.FunctionalTest;
import com.bbcc.kidly.support.TestTokens;
import jakarta.persistence.EntityManager;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 관리자가 상품 유형 설정을 바꾸는 API를 HTTP 요청부터 응답까지 확인한다.
 * 기준 데이터와 상품은 리포지토리 없이 EntityManager로 저장한다.
 */
@FunctionalTest
class AdminProductTypeChangeApiTest {

    private static final Long ADMIN_ID = 3L;

    @Autowired
    private RestTestClient client;

    @Autowired
    private TestTokens tokens;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private ProductRepository productRepository;

    private ProductFixture.Clothing clothing;
    private Attribute hatSize;
    private AttributeValue size48;
    private ProductType hatType;

    // 의류 유형과 모자 유형(색상 필수 옵션, 모자 사이즈 사이즈 역할 옵션)을 저장한다
    @BeforeEach
    void saveReferenceData() {
        clothing = ProductFixture.clothing();
        hatSize = Attribute.create("모자 사이즈", "HAT_SIZE", AttributeInputType.LIST);
        size48 = AttributeValue.create(hatSize, "48cm", null);
        AttributeValue sizeFree = AttributeValue.create(hatSize, "FREE", null);
        hatType = ProductType.define("모자", List.of(
            ProductTypeFixture.requiredOption(clothing.color()), ProductTypeFixture.sizeOption(hatSize)));
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            for (ProductTypeAttribute link : clothing.type().getAttributes()) {
                entityManager.persist(link.getAttribute());
            }
            entityManager.persist(hatSize);
            for (AttributeValue value : List.of(clothing.cotton(), clothing.other(), clothing.linen(),
                clothing.brushed(), clothing.stretch(), size48, sizeFree)) {
                entityManager.persist(value);
            }
            entityManager.persist(clothing.type());
            entityManager.persist(hatType);
        });
    }

    @Test
    @DisplayName("상품이 없는 유형의 연결을 바꾸면 200과 넘긴 순서의 연결, 허용 값을 준다")
    void changeProductType() {
        put(typePath(hatType), Map.of("attributes", List.of(
            link(hatSize.getId(), "OPTION", true, false, true, List.of(size48.getId())),
            link(clothing.color().getId(), "OPTION", true, true, false, List.of()),
            link(clothing.mainMaterial().getId(), "INFO", true, true, false, List.of()))))
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.productTypeId").isEqualTo(String.valueOf(hatType.getId()))
            .jsonPath("$.name").isEqualTo("모자")
            .jsonPath("$.attributes[*].code").isEqualTo(List.of("HAT_SIZE", "COLOR", "MAIN_MATERIAL"))
            .jsonPath("$.attributes[*].sizeRole").isEqualTo(List.of(true, false, false))
            .jsonPath("$.attributes[0].allValuesAllowed").isEqualTo(false)
            .jsonPath("$.attributes[0].allowedValues[*].name").isEqualTo(List.of("48cm"))
            .jsonPath("$.attributes[2].role").isEqualTo("INFO");
    }

    @Test
    @DisplayName("TYPE-07 상품이 있는 유형에 필수 정보 항목을 더하면 409로 거절한다")
    void rejectTypeChangeBreakingProducts() {
        saveProductOfClothingType();

        assertProblem(put(typePath(clothing.type()), Map.of("attributes", List.of(
            link(clothing.color().getId(), "OPTION", true, true, false, List.of()),
            link(sizeOf(clothing).getId(), "OPTION", true, true, true, List.of()),
            link(clothing.mainMaterial().getId(), "INFO", true, true, false, List.of()),
            link(clothing.fabricComposition().getId(), "INFO", false, false, false, List.of()),
            link(clothing.feature().getId(), "INFO", false, true, false, List.of()),
            link(hatSize.getId(), "INFO", true, true, false, List.of())))), HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("없는 유형 · 항목 · 허용 값이면 404로 거절한다")
    void rejectUnknownTypeReferences() {
        Map<String, Object> body = Map.of("attributes", List.of(
            link(clothing.color().getId(), "OPTION", true, true, false, List.of())));

        assertProblem(put("/api/v1/admin/product-types/999999", body), HttpStatus.NOT_FOUND);
        assertProblem(put(typePath(hatType), Map.of("attributes", List.of(
            link(999999L, "OPTION", true, true, false, List.of())))), HttpStatus.NOT_FOUND);
        assertProblem(put(typePath(hatType), Map.of("attributes", List.of(
            link(hatSize.getId(), "OPTION", true, false, true, List.of(999999L))))), HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("TYPE-02 필수 옵션 항목이 없거나, 연결 목록 · 역할이 잘못되면 400으로 거절한다")
    void rejectInvalidTypeChange() {
        Map<String, Object> withoutAttributes = new HashMap<>();
        withoutAttributes.put("attributes", null);

        assertProblem(put(typePath(hatType), Map.of("attributes", List.of(
            link(clothing.color().getId(), "OPTION", false, true, false, List.of())))), HttpStatus.BAD_REQUEST);
        assertProblem(put(typePath(hatType), withoutAttributes), HttpStatus.BAD_REQUEST);
        assertProblem(put(typePath(hatType), Map.of("attributes", List.of(
            link(clothing.color().getId(), "SIZE", true, true, false, List.of())))), HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("관리자가 아니면 상품 유형 설정을 바꿀 수 없다. 비로그인은 401, 구매자 · 판매자는 403이다")
    void rejectNonAdminTypeChange() {
        Map<String, Object> body = Map.of("attributes", List.of(
            link(clothing.color().getId(), "OPTION", true, true, false, List.of())));

        putWith(null, typePath(hatType), body).expectStatus().isUnauthorized();
        putWith(tokens.buyer(1L), typePath(hatType), body).expectStatus().isForbidden();
        putWith(tokens.seller(2L), typePath(hatType), body).expectStatus().isForbidden();
    }

    // 의류 유형을 쓰는 의류 > 상의 카테고리에 상품 A를 저장한다
    private void saveProductOfClothingType() {
        Product.RegisterInfo info = ProductFixture.productA(clothing);
        Category top = info.category();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            entityManager.persist(top.getParent());
            top.getMeasurementItems().forEach(link -> entityManager.persist(link.getMeasurementItem()));
            entityManager.persist(top);
            productRepository.save(Product.register(info));
        });
    }

    // 의류 유형의 두 번째 연결이 사이즈 항목이다
    private static Attribute sizeOf(ProductFixture.Clothing clothing) {
        return clothing.type().getAttributes().get(1).getAttribute();
    }

    // 실패 응답이 ProblemDetail 5필드를 담은 application/problem+json인지 확인한다
    private void assertProblem(RestTestClient.ResponseSpec response, HttpStatus status) {
        response.expectStatus().isEqualTo(status)
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.type").exists()
            .jsonPath("$.title").exists()
            .jsonPath("$.status").isEqualTo(status.value())
            .jsonPath("$.detail").exists()
            .jsonPath("$.instance").exists();
    }

    private static String typePath(ProductType type) {
        return "/api/v1/admin/product-types/" + type.getId();
    }

    // 가격 구분은 이 테스트에서 쓰지 않아 늘 false로 보낸다. ID가 null인 연결도 만들 수 있게 HashMap을 쓴다
    private static Map<String, Object> link(Long attributeId, String role, boolean required,
        boolean allValuesAllowed, boolean sizeRole, List<Long> allowedValueIds) {
        Map<String, Object> link = new HashMap<>();
        link.put("attributeId", attributeId == null ? null : String.valueOf(attributeId));
        link.put("role", role);
        link.put("required", required);
        link.put("allValuesAllowed", allValuesAllowed);
        link.put("sizeRole", sizeRole);
        link.put("priceAffecting", false);
        link.put("allowedValueIds", allowedValueIds.stream().map(String::valueOf).toList());
        return link;
    }

    private RestTestClient.ResponseSpec put(String path, Object body) {
        return putWith(tokens.admin(ADMIN_ID), path, body);
    }

    private RestTestClient.ResponseSpec putWith(String token, String path, Object body) {
        return client.put().uri(path)
            .headers(headers -> {
                if (token != null) {
                    headers.setBearerAuth(token);
                }
            })
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .exchange();
    }
}
