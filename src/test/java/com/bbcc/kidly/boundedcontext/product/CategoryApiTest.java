package com.bbcc.kidly.boundedcontext.product;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.product.domain.type.Attribute;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeValue;
import com.bbcc.kidly.boundedcontext.product.domain.type.Category;
import com.bbcc.kidly.boundedcontext.product.domain.type.MeasurementItem;
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

    @Test
    @DisplayName("말단 카테고리의 입력 항목은 옵션 항목부터 연결 순서로, 유형이 허용한 값과 실측 항목을 연결 순서로 받는다")
    void getInputAttributesOfLeafCategory() {
        Long topId = inTransaction(() -> {
            Attribute color = persist(ProductTypeFixture.color());
            Attribute size = persist(ProductTypeFixture.size());
            Attribute mainMaterial = persist(ProductTypeFixture.mainMaterial());
            Attribute fabricComposition = persist(ProductTypeFixture.fabricComposition());
            persist(AttributeValue.create(color, "네이비", "#2f3e5c"));
            persist(AttributeValue.create(color, "오트밀", "#e3d3bc"));
            persist(AttributeValue.create(size, "105", null));
            AttributeValue size110 = persist(AttributeValue.create(size, "110", null));
            AttributeValue size120 = persist(AttributeValue.create(size, "120", null));
            persist(AttributeValue.create(mainMaterial, "면", null));
            ProductType clothingType = persist(ProductType.define("의류", List.of(
                ProductTypeFixture.requiredOption(color),
                ProductTypeFixture.requiredInfo(mainMaterial),
                ProductTypeFixture.restrictedSizeOption(size, List.of(size120, size110)),
                ProductTypeFixture.optionalTextInfo(fabricComposition))));
            // 실측 항목은 연결 순서와 반대로 저장해, 실측 항목 ID 순서가 연결 순서와 다르게 한다
            List<MeasurementItem> items = List.of(CategoryFixture.totalLength(), CategoryFixture.shoulderWidth(),
                CategoryFixture.chestWidth(), CategoryFixture.sleeveLength());
            for (int i = items.size() - 1; i >= 0; i--) {
                persist(items.get(i));
            }
            Category clothing = persist(Category.createMiddle("의류", null));
            return persist(Category.createLeaf(new Category.LeafInfo("상의", clothing, clothingType, items))).getId();
        });

        client.get().uri("/api/v1/categories/{categoryId}/attributes", topId)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.categoryId").isEqualTo(String.valueOf(topId))
            .jsonPath("$.categoryName").isEqualTo("상의")
            .jsonPath("$.productTypeId").value(id -> assertThat(id).isInstanceOf(String.class))
            .jsonPath("$.productTypeName").isEqualTo("의류")
            .jsonPath("$.attributes[*].code").isEqualTo(List.of("COLOR", "SIZE", "MAIN_MATERIAL", "FABRIC_COMPOSITION"))
            .jsonPath("$.attributes[0].attributeId").value(id -> assertThat(id).isInstanceOf(String.class))
            .jsonPath("$.attributes[0].name").isEqualTo("색상")
            .jsonPath("$.attributes[0].inputType").isEqualTo("PALETTE")
            .jsonPath("$.attributes[0].role").isEqualTo("OPTION")
            .jsonPath("$.attributes[0].required").isEqualTo(true)
            .jsonPath("$.attributes[0].sizeRole").isEqualTo(false)
            .jsonPath("$.attributes[0].priceAffecting").isEqualTo(false)
            .jsonPath("$.attributes[0].values[*].value").isEqualTo(List.of("네이비", "오트밀"))
            .jsonPath("$.attributes[0].values[0].valueId").value(id -> assertThat(id).isInstanceOf(String.class))
            .jsonPath("$.attributes[0].values[0].colorCode").isEqualTo("#2f3e5c")
            .jsonPath("$.attributes[1].sizeRole").isEqualTo(true)
            .jsonPath("$.attributes[1].values[*].value").isEqualTo(List.of("110", "120"))
            .jsonPath("$.attributes[1].values[0].colorCode").hasJsonPath()
            .jsonPath("$.attributes[1].values[0].colorCode").isEqualTo(null)
            .jsonPath("$.attributes[2].role").isEqualTo("INFO")
            .jsonPath("$.attributes[2].values[*].value").isEqualTo(List.of("면"))
            .jsonPath("$.attributes[3].inputType").isEqualTo("TEXT")
            .jsonPath("$.attributes[3].required").isEqualTo(false)
            .jsonPath("$.attributes[3].values").isEmpty()
            .jsonPath("$.measurementItems[*].code")
            .isEqualTo(List.of("TOTAL_LENGTH", "SHOULDER_WIDTH", "CHEST_WIDTH", "SLEEVE_LENGTH"))
            .jsonPath("$.measurementItems[0].name").isEqualTo("총장")
            .jsonPath("$.measurementItems[0].unit").isEqualTo("cm");
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
