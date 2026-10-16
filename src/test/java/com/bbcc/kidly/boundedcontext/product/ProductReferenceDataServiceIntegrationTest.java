package com.bbcc.kidly.boundedcontext.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.bbcc.kidly.boundedcontext.product.app.type.CategoryService;
import com.bbcc.kidly.boundedcontext.product.app.type.ProductReferenceDataService;
import com.bbcc.kidly.boundedcontext.product.app.type.dto.CategoryInputsResult;
import com.bbcc.kidly.boundedcontext.product.app.type.dto.CategoryTreeResult;
import com.bbcc.kidly.boundedcontext.product.domain.type.Attribute;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeRepository;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductType;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductTypeRepository;
import com.bbcc.kidly.support.IntegrationTest;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class ProductReferenceDataServiceIntegrationTest {

    @Autowired
    private ProductReferenceDataService productReferenceDataService;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private AttributeRepository attributeRepository;

    @Autowired
    private ProductTypeRepository productTypeRepository;

    @Test
    @DisplayName("의류 유형과 항목 · 목록 값 · 실측 항목을 넣고, 의류와 잡화 아래에 말단 카테고리를 둔다")
    void load() {
        productReferenceDataService.load();

        List<CategoryTreeResult> tree = categoryService.findTree();
        assertThat(tree).extracting(CategoryTreeResult::name).containsExactly("의류", "잡화");
        List<CategoryTreeResult> clothes = tree.get(0).children();
        List<CategoryTreeResult> goods = tree.get(1).children();
        assertThat(clothes).extracting(CategoryTreeResult::name).containsExactly("상의", "하의", "아우터", "원피스");
        assertThat(goods).extracting(CategoryTreeResult::name).containsExactly("모자", "양말");

        CategoryInputsResult top = categoryService.findInputs(clothes.get(0).categoryId());
        assertThat(top.productTypeName()).isEqualTo("의류");
        assertThat(top.attributes())
            .extracting(attribute -> attribute.code(), attribute -> attribute.role(), attribute -> attribute.required())
            .containsExactly(
                tuple("COLOR", "OPTION", true),
                tuple("SIZE", "OPTION", true),
                tuple("MAIN_MATERIAL", "INFO", true),
                tuple("FABRIC_COMPOSITION", "INFO", false));
        assertThat(top.attributes().get(0).values()).hasSize(12)
            .first().isEqualTo(new CategoryInputsResult.SelectableValue(
                top.attributes().get(0).values().get(0).valueId(), "네이비", "#2f3e5c"));
        assertThat(top.attributes().get(1).values()).extracting(CategoryInputsResult.SelectableValue::value)
            .containsExactly("90", "100", "110", "120", "130", "140", "150", "FREE");
        assertThat(top.attributes().get(2).values()).extracting(CategoryInputsResult.SelectableValue::value)
            .containsExactly("면", "폴리에스테르", "아크릴", "기타");
        assertThat(top.attributes().get(3).values()).isEmpty();

        assertThat(measurementCodes(clothes.get(0)))
            .containsExactly("TOTAL_LENGTH", "SHOULDER_WIDTH", "CHEST_WIDTH", "SLEEVE_LENGTH");
        assertThat(measurementCodes(clothes.get(1)))
            .containsExactly("TOTAL_LENGTH", "WAIST_WIDTH", "HIP_WIDTH", "THIGH_WIDTH", "RISE");
        assertThat(measurementCodes(clothes.get(2)))
            .containsExactly("TOTAL_LENGTH", "SHOULDER_WIDTH", "CHEST_WIDTH", "SLEEVE_LENGTH");
        assertThat(measurementCodes(clothes.get(3))).containsExactly("TOTAL_LENGTH", "SHOULDER_WIDTH", "CHEST_WIDTH");
        assertThat(measurementCodes(goods.get(0))).containsExactly("HEAD_CIRCUMFERENCE");
        assertThat(measurementCodes(goods.get(1))).containsExactly("FOOT_LENGTH");
    }

    @Test
    @DisplayName("상품 유형이 하나라도 있으면 기준 데이터를 넣지 않는다")
    void skipWhenProductTypeExists() {
        Attribute color = attributeRepository.save(ProductTypeFixture.color());
        productTypeRepository.save(ProductType.define("신발", List.of(ProductTypeFixture.requiredOption(color))));

        productReferenceDataService.load();

        assertThat(categoryService.findTree()).isEmpty();
    }

    private List<String> measurementCodes(CategoryTreeResult category) {
        return categoryService.findInputs(category.categoryId()).measurementItems().stream()
            .map(CategoryInputsResult.Measurement::code)
            .toList();
    }
}
