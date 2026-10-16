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
            .extracting(attribute -> attribute.code(), attribute -> attribute.name(),
                attribute -> attribute.inputType(), attribute -> attribute.role(),
                attribute -> attribute.required(), attribute -> attribute.sizeRole())
            .containsExactly(
                tuple("COLOR", "색상", "PALETTE", "OPTION", true, false),
                tuple("SIZE", "사이즈", "LIST", "OPTION", true, true),
                tuple("MAIN_MATERIAL", "주요소재", "LIST", "INFO", true, false),
                tuple("FABRIC_COMPOSITION", "혼용률", "TEXT", "INFO", false, false));
        assertThat(top.attributes().get(0).values())
            .extracting(CategoryInputsResult.SelectableValue::value, CategoryInputsResult.SelectableValue::colorCode)
            .containsExactly(
                tuple("네이비", "#2f3e5c"), tuple("그레이", "#9e9e9e"), tuple("오트밀", "#e3d3bc"),
                tuple("코랄 핑크", "#e8a598"), tuple("화이트", "#ffffff"), tuple("블루", "#3f6fb5"),
                tuple("베이지", "#d8c3a5"), tuple("크림", "#f3ead3"), tuple("블랙", "#222222"),
                tuple("레드", "#d64545"), tuple("옐로", "#f2c94c"), tuple("그린", "#6aa36f"));
        assertThat(top.attributes().get(1).values()).extracting(CategoryInputsResult.SelectableValue::value)
            .containsExactly("90", "100", "110", "120", "130", "140", "150", "FREE");
        assertThat(top.attributes().get(2).values()).extracting(CategoryInputsResult.SelectableValue::value)
            .containsExactly("면", "폴리에스테르", "아크릴", "기타");
        assertThat(top.attributes().get(3).values()).isEmpty();

        assertThat(top.measurementItems())
            .extracting(CategoryInputsResult.Measurement::code, CategoryInputsResult.Measurement::name,
                CategoryInputsResult.Measurement::unit)
            .containsExactly(
                tuple("TOTAL_LENGTH", "총장", "cm"), tuple("SHOULDER_WIDTH", "어깨너비", "cm"),
                tuple("CHEST_WIDTH", "가슴단면", "cm"), tuple("SLEEVE_LENGTH", "소매길이", "cm"));
        assertThat(measurementCodes(clothes.get(1)))
            .containsExactly("TOTAL_LENGTH", "WAIST_WIDTH", "HIP_WIDTH", "THIGH_WIDTH", "RISE");
        assertThat(measurementCodes(clothes.get(2)))
            .containsExactly("TOTAL_LENGTH", "SHOULDER_WIDTH", "CHEST_WIDTH", "SLEEVE_LENGTH");
        assertThat(measurementCodes(clothes.get(3))).containsExactly("TOTAL_LENGTH", "SHOULDER_WIDTH", "CHEST_WIDTH");
        assertThat(measurementCodes(goods.get(0))).containsExactly("HEAD_CIRCUMFERENCE");
        assertThat(measurementCodes(goods.get(1))).containsExactly("FOOT_LENGTH");
        assertThat(categoryService.findInputs(goods.get(0).categoryId()).productTypeName()).isEqualTo("의류");
        assertThat(categoryService.findInputs(goods.get(1).categoryId()).productTypeName()).isEqualTo("의류");
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
