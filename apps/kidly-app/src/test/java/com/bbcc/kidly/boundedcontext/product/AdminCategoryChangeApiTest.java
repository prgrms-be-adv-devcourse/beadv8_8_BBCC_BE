package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProblemDetailAssertions.assertProblem;
import com.bbcc.kidly.boundedcontext.product.domain.product.Product;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductRepository;
import com.bbcc.kidly.boundedcontext.product.domain.type.Attribute;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeInputType;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeValue;
import com.bbcc.kidly.boundedcontext.product.domain.type.Category;
import com.bbcc.kidly.boundedcontext.product.domain.type.MeasurementItem;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductType;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductTypeAttribute;
import com.bbcc.kidly.global.security.TestMembers;
import com.bbcc.kidly.support.FunctionalTest;
import jakarta.persistence.EntityManager;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 관리자가 카테고리 설정을 바꾸는 API를 HTTP 요청부터 응답까지 확인한다.
 * 기준 데이터와 상품은 리포지토리 없이 EntityManager로 저장한다.
 */
@FunctionalTest
class AdminCategoryChangeApiTest {

    private static final Long ADMIN_ID = 3L;

    @Autowired
    private RestTestClient client;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private ProductRepository productRepository;

    private ProductFixture.Clothing clothing;
    private ProductType hatType;
    private MeasurementItem headCircumference;
    private Category goods;
    private Category hat;

    // 의류 유형, 모자 유형(색상 · 모자 사이즈), 잡화 > 모자(의류 유형, 필수 실측 머리둘레)를 저장한다
    @BeforeEach
    void saveReferenceData() {
        clothing = ProductFixture.clothing();
        Attribute hatSize = Attribute.create("모자 사이즈", "HAT_SIZE", AttributeInputType.LIST);
        hatType = ProductType.define("모자", List.of(
            ProductTypeFixture.requiredOption(clothing.color()), ProductTypeFixture.sizeOption(hatSize)));
        headCircumference = CategoryFixture.headCircumference();
        goods = Category.createMiddle("잡화", null);
        hat = Category.createLeaf(new Category.LeafInfo("모자", goods, clothing.type(), List.of(headCircumference)));
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            for (ProductTypeAttribute link : clothing.type().getAttributes()) {
                entityManager.persist(link.getAttribute());
            }
            for (AttributeValue value : List.of(
                clothing.cotton(), clothing.other(), clothing.linen(), clothing.brushed(), clothing.stretch())) {
                entityManager.persist(value);
            }
            entityManager.persist(hatSize);
            entityManager.persist(clothing.type());
            entityManager.persist(hatType);
            entityManager.persist(headCircumference);
            entityManager.persist(goods);
            entityManager.persist(hat);
        });
    }

    @Test
    @DisplayName("상품이 없는 말단 카테고리의 상품 유형과 필수 실측 항목을 바꾸면 200과 바뀐 카테고리를 준다")
    void changeCategory() {
        put(categoryPath(hat), Map.of(
            "productTypeId", String.valueOf(hatType.getId()),
            "measurementItemIds", List.of(String.valueOf(headCircumference.getId()))))
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.categoryId").isEqualTo(String.valueOf(hat.getId()))
            .jsonPath("$.name").isEqualTo("모자")
            .jsonPath("$.leaf").isEqualTo(true)
            .jsonPath("$.productTypeId").isEqualTo(String.valueOf(hatType.getId()))
            .jsonPath("$.productTypeName").isEqualTo("모자")
            .jsonPath("$.measurementItems[*].code").isEqualTo(List.of("HEAD_CIRCUMFERENCE"));
    }

    @Test
    @DisplayName("TYPE-07 상품이 있는 카테고리의 상품 유형을 바꾸면 409로 거절한다")
    void rejectCategoryChangeBreakingProducts() {
        Category top = savedProductCategory();

        assertProblem(put(categoryPath(top), categoryChange(hatType.getId(), List.of())), HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("TYPE-07 상품이 있는 카테고리도 상품 유형을 그대로 두고 필수 실측 항목만 빼면 200으로 받는다")
    void acceptSameTypeChangeWithProducts() {
        Category top = savedProductCategory();

        put(categoryPath(top), categoryChange(clothing.type().getId(), List.of()))
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.productTypeId").isEqualTo(String.valueOf(clothing.type().getId()))
            .jsonPath("$.measurementItems").isEmpty();
    }

    @Test
    @DisplayName("없는 카테고리 · 상품 유형 · 실측 항목이면 404로 거절한다")
    void rejectUnknownCategoryReferences() {
        String unknownCategoryPath = "/api/v1/admin/categories/999999";

        assertProblem(put(unknownCategoryPath, categoryChange(hatType.getId(), List.of())), HttpStatus.NOT_FOUND);
        assertProblem(put(categoryPath(hat), categoryChange(999999L, List.of())), HttpStatus.NOT_FOUND);
        assertProblem(put(categoryPath(hat), categoryChange(hatType.getId(), List.of(999999L))),
            HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("TYPE-01 중간 카테고리를 바꾸거나, 상품 유형 · 실측 항목 ID가 비면 400으로 거절한다")
    void rejectInvalidCategoryChange() {
        assertProblem(put(categoryPath(goods), categoryChange(hatType.getId(), List.of())), HttpStatus.BAD_REQUEST);
        assertProblem(put(categoryPath(hat), categoryChange(null, List.of())), HttpStatus.BAD_REQUEST);
        assertProblem(put(categoryPath(hat), categoryChange(hatType.getId(), null)), HttpStatus.BAD_REQUEST);
        assertProblem(put(categoryPath(hat), categoryChange(hatType.getId(), Arrays.asList((Long) null))),
            HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("관리자가 아니면 카테고리 설정을 바꿀 수 없다. 비로그인은 401, 구매자 · 판매자는 403이다")
    void rejectNonAdminCategoryChange() {
        Map<String, Object> body = categoryChange(hatType.getId(), List.of());

        putWith(TestMembers.anonymous(), categoryPath(hat), body).expectStatus().isUnauthorized();
        putWith(TestMembers.buyer(1L), categoryPath(hat), body).expectStatus().isForbidden();
        putWith(TestMembers.seller(2L), categoryPath(hat), body).expectStatus().isForbidden();
    }

    // 상품 A(의류 > 상의, 의류 유형)를 저장하고 그 카테고리를 돌려준다
    private Category savedProductCategory() {
        Product.RegisterInfo info = ProductFixture.productA(clothing);
        Category top = info.category();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            entityManager.persist(top.getParent());
            top.getMeasurementItems().forEach(link -> entityManager.persist(link.getMeasurementItem()));
            entityManager.persist(top);
            productRepository.save(Product.register(info));
        });
        return top;
    }

    // ID 목록 · ID가 null인 요청도 만들 수 있게 Map.of 대신 HashMap을 쓴다
    private static Map<String, Object> categoryChange(Long productTypeId, List<Long> measurementItemIds) {
        Map<String, Object> body = new HashMap<>();
        body.put("productTypeId", productTypeId == null ? null : String.valueOf(productTypeId));
        body.put("measurementItemIds", measurementItemIds == null ? null
            : measurementItemIds.stream().map(id -> id == null ? null : String.valueOf(id)).toList());
        return body;
    }

    private static String categoryPath(Category category) {
        return "/api/v1/admin/categories/" + category.getId();
    }

    private RestTestClient.ResponseSpec put(String path, Object body) {
        return putWith(TestMembers.admin(ADMIN_ID), path, body);
    }

    private RestTestClient.ResponseSpec putWith(Consumer<HttpHeaders> login, String path, Object body) {
        return client.put().uri(path)
            .headers(login)
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .exchange();
    }
}
