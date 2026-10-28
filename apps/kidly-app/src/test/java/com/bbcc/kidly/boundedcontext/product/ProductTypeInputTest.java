package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProductTypeFixture.color;
import static com.bbcc.kidly.boundedcontext.product.ProductTypeFixture.fabricComposition;
import static com.bbcc.kidly.boundedcontext.product.ProductTypeFixture.mainMaterial;
import static com.bbcc.kidly.boundedcontext.product.ProductTypeFixture.optionalTextInfo;
import static com.bbcc.kidly.boundedcontext.product.ProductTypeFixture.requiredInfo;
import static com.bbcc.kidly.boundedcontext.product.ProductTypeFixture.requiredOption;
import static com.bbcc.kidly.boundedcontext.product.ProductTypeFixture.restrictedOption;
import static com.bbcc.kidly.boundedcontext.product.ProductTypeFixture.size;
import static com.bbcc.kidly.boundedcontext.product.ProductTypeFixture.sizeOption;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.product.domain.type.Attribute;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeInputType;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeValue;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductType;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 상품 등록 화면이 받는 입력 항목을 상품 유형이 어떻게 정하는지 확인한다.
 */
class ProductTypeInputTest {

    @Test
    @DisplayName("OPT-09 입력 항목은 옵션 항목을 먼저 두고, 각 역할 안에서는 유형에 연결한 순서를 따른다")
    void inputAttributesPutOptionsFirstInLinkOrder() {
        ProductType type = ProductType.define("의류", List.of(
            requiredInfo(mainMaterial()),
            requiredOption(color()),
            optionalTextInfo(fabricComposition()),
            sizeOption(size())));

        assertThat(type.inputAttributes())
            .extracting(link -> link.getAttribute().getCode())
            .containsExactly("COLOR", "SIZE", "MAIN_MATERIAL", "FABRIC_COMPOSITION");
    }

    @Test
    @DisplayName("TYPE-06 고를 수 있는 값은 모든 값 허용 항목이면 그 항목의 값 전부, 아니면 유형이 허용한 값만이고, 받은 순서를 지킨다")
    void selectableValuesKeepAllowedValuesInGivenOrder() {
        Attribute color = color();
        Attribute size = size();
        Attribute pattern = Attribute.create("무늬", "PATTERN", AttributeInputType.LIST);
        AttributeValue navy = AttributeValue.create(color, "네이비", "#2f3e5c");
        AttributeValue oatmeal = AttributeValue.create(color, "오트밀", "#e3d3bc");
        AttributeValue size105 = AttributeValue.create(size, "105", null);
        AttributeValue size110 = AttributeValue.create(size, "110", null);
        AttributeValue size120 = AttributeValue.create(size, "120", null);
        AttributeValue stripe = AttributeValue.create(pattern, "스트라이프", null);
        ProductType type = ProductType.define("의류", List.of(
            requiredOption(color),
            restrictedOption(size, List.of(size120, size110)),
            optionalTextInfo(fabricComposition())));

        assertThat(type.selectableValues(List.of(navy, size105, oatmeal, size110, stripe, size120)))
            .containsExactly(navy, oatmeal, size110, size120);
    }
}
