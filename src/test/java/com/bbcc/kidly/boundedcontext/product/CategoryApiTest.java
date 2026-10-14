package com.bbcc.kidly.boundedcontext.product;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.product.domain.type.Attribute;
import com.bbcc.kidly.boundedcontext.product.domain.type.Category;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductType;
import com.bbcc.kidly.support.FunctionalTest;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 상품 등록 화면이 부르는 카테고리 조회 API를 HTTP 요청부터 응답까지 확인한다.
 * 카테고리 · 상품 유형 · 항목 · 실측 항목은 리포지토리 없이 EntityManager로 저장한다.
 */
@FunctionalTest
class CategoryApiTest {

    @Autowired
    private RestTestClient client;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("카테고리 목록은 로그인 없이 계층대로 받고, 같은 부모 아래에서는 ID 순서이며 하위가 없으면 빈 목록이다")
    void listCategoriesAsTree() {
        inTransaction(() -> {
            ProductType clothingType = savedClothingType();
            Category clothing = persist(Category.createMiddle("의류", null));
            persist(Category.createLeaf(new Category.LeafInfo("하의", clothing, clothingType, List.of())));
            persist(Category.createLeaf(new Category.LeafInfo("상의", clothing, clothingType, List.of())));
            persist(Category.createMiddle("잡화", null));
            return null;
        });

        client.get().uri("/api/v1/categories")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.length()").isEqualTo(2)
            .jsonPath("$[0].name").isEqualTo("의류")
            .jsonPath("$[0].categoryId").value(id -> assertThat(id).isInstanceOf(String.class))
            .jsonPath("$[0].leaf").isEqualTo(false)
            .jsonPath("$[0].children[*].name").isEqualTo(List.of("하의", "상의"))
            .jsonPath("$[0].children[0].leaf").isEqualTo(true)
            .jsonPath("$[0].children[0].children").isEmpty()
            .jsonPath("$[1].name").isEqualTo("잡화")
            .jsonPath("$[1].leaf").isEqualTo(false)
            .jsonPath("$[1].children").isEmpty();
    }

    // 색상만 옵션 항목으로 둔 의류 유형을 저장한다. 항목은 cascade되지 않아 먼저 저장한다
    private ProductType savedClothingType() {
        Attribute color = persist(ProductTypeFixture.color());
        return persist(ProductType.define("의류", List.of(ProductTypeFixture.requiredOption(color))));
    }

    private <T> T persist(T entity) {
        entityManager.persist(entity);
        return entity;
    }

    private <T> T inTransaction(Supplier<T> work) {
        return new TransactionTemplate(transactionManager).execute(status -> work.get());
    }
}
