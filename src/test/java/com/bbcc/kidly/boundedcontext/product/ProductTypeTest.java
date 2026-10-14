package com.bbcc.kidly.boundedcontext.product;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.product.domain.type.ProductType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProductTypeTest {

    @Test
    @DisplayName("TYPE-02 필수 옵션 항목이 있으면 유형을 만든다")
    void defineTypeWithRequiredOptionAttribute() {
        ProductType clothing = ProductType.define("의류", ProductTypeFixture.clothingLinks());

        assertThat(clothing.getName()).isEqualTo("의류");
        assertThat(clothing.getAttributes())
            .extracting(link -> link.getAttribute().getCode())
            .containsExactly("COLOR", "SIZE", "MAIN_MATERIAL", "FABRIC_COMPOSITION");
    }
}
