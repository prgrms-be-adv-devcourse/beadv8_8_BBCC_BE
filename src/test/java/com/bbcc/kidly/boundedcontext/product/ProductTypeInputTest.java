package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProductTypeFixture.color;
import static com.bbcc.kidly.boundedcontext.product.ProductTypeFixture.fabricComposition;
import static com.bbcc.kidly.boundedcontext.product.ProductTypeFixture.mainMaterial;
import static com.bbcc.kidly.boundedcontext.product.ProductTypeFixture.optionalTextInfo;
import static com.bbcc.kidly.boundedcontext.product.ProductTypeFixture.requiredInfo;
import static com.bbcc.kidly.boundedcontext.product.ProductTypeFixture.requiredOption;
import static com.bbcc.kidly.boundedcontext.product.ProductTypeFixture.size;
import static com.bbcc.kidly.boundedcontext.product.ProductTypeFixture.sizeOption;
import static org.assertj.core.api.Assertions.assertThat;

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
}
