package com.bbcc.kidly.boundedcontext.product;

import com.bbcc.kidly.boundedcontext.product.domain.type.Attribute;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeInputType;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeValue;
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
