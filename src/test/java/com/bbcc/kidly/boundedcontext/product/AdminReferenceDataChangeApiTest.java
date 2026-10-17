package com.bbcc.kidly.boundedcontext.product;

import com.bbcc.kidly.boundedcontext.product.domain.type.Attribute;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeInputType;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeValue;
import com.bbcc.kidly.boundedcontext.product.domain.type.Category;
import com.bbcc.kidly.boundedcontext.product.domain.type.MeasurementItem;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductType;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductTypeAttribute;
import com.bbcc.kidly.support.FunctionalTest;
import com.bbcc.kidly.support.TestTokens;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 관리자가 카테고리 · 상품 유형 설정을 바꾸는 API를 HTTP 요청부터 응답까지 확인한다.
 * 기준 데이터와 상품은 리포지토리 없이 EntityManager로 저장한다.
 */
@FunctionalTest
class AdminReferenceDataChangeApiTest {

    private static final Long ADMIN_ID = 3L;

    @Autowired
    private RestTestClient client;

    @Autowired
    private TestTokens tokens;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private ProductFixture.Clothing clothing;
    private ProductType hatType;
    private MeasurementItem headCircumference;
    private Category hat;

    // 의류 유형, 모자 유형(색상 · 모자 사이즈), 잡화 > 모자(의류 유형, 필수 실측 머리둘레)를 저장한다
    @BeforeEach
    void saveReferenceData() {
        clothing = ProductFixture.clothing();
        Attribute hatSize = Attribute.create("모자 사이즈", "HAT_SIZE", AttributeInputType.LIST);
        hatType = ProductType.define("모자", List.of(
            ProductTypeFixture.requiredOption(clothing.color()), ProductTypeFixture.sizeOption(hatSize)));
        headCircumference = MeasurementItem.create("머리둘레", "HEAD_CIRCUMFERENCE", "cm");
        Category goods = Category.createMiddle("잡화", null);
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

    private static String categoryPath(Category category) {
        return "/api/v1/admin/categories/" + category.getId();
    }

    private RestTestClient.ResponseSpec put(String path, Object body) {
        return client.put().uri(path)
            .headers(headers -> headers.setBearerAuth(tokens.admin(ADMIN_ID)))
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .exchange();
    }
}
