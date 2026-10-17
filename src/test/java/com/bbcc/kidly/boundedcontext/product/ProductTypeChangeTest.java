package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProductTypeFixture.color;
import static com.bbcc.kidly.boundedcontext.product.ProductTypeFixture.fabricComposition;
import static com.bbcc.kidly.boundedcontext.product.ProductTypeFixture.mainMaterial;
import static com.bbcc.kidly.boundedcontext.product.ProductTypeFixture.optionalOption;
import static com.bbcc.kidly.boundedcontext.product.ProductTypeFixture.optionalTextInfo;
import static com.bbcc.kidly.boundedcontext.product.ProductTypeFixture.requiredInfo;
import static com.bbcc.kidly.boundedcontext.product.ProductTypeFixture.requiredOption;
import static com.bbcc.kidly.boundedcontext.product.ProductTypeFixture.size;
import static com.bbcc.kidly.boundedcontext.product.ProductTypeFixture.sizeOption;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import com.bbcc.kidly.boundedcontext.product.domain.type.Attribute;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeRole;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductType;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductTypeAttribute;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProductTypeChangeTest {

    private final Attribute color = color();
    private final Attribute size = size();
    private final Attribute material = mainMaterial();
    private final Attribute composition = fabricComposition();

    // 기준 데이터의 의류와 같은 연결: 색상 · 사이즈 필수 옵션, 주요소재 필수 정보, 혼용률 선택 정보
    private ProductType clothing() {
        return ProductType.define("의류", List.of(
            requiredOption(color), sizeOption(size), requiredInfo(material), optionalTextInfo(composition)));
    }

    @Test
    @DisplayName("상품이 없으면 연결을 빼고 역할 · 필수 · 순서를 바꾼 목록으로 통째로 바꾼다")
    void replaceLinksWithoutProducts() {
        ProductType type = clothing();

        type.change(List.of(sizeOption(size), requiredOption(material), optionalOption(color)), false);

        assertThat(type.getAttributes())
            .extracting(link -> link.getAttribute().getCode(), ProductTypeAttribute::getRole,
                ProductTypeAttribute::isRequired, ProductTypeAttribute::isSizeRole, ProductTypeAttribute::getSortOrder)
            .containsExactly(
                tuple("SIZE", AttributeRole.OPTION, true, true, 1),
                tuple("MAIN_MATERIAL", AttributeRole.OPTION, true, false, 2),
                tuple("COLOR", AttributeRole.OPTION, false, false, 3));
    }
}
