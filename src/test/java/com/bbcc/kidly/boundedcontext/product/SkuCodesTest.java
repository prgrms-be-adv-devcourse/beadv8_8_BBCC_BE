package com.bbcc.kidly.boundedcontext.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.product.domain.product.ProductErrorCode;
import com.bbcc.kidly.boundedcontext.product.domain.product.SkuCodes;
import com.bbcc.kidly.global.error.BusinessException;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SkuCodesTest {

    @Test
    @DisplayName("OPT-07 판매자가 이미 쓰는 SKU 코드를 넣으면 409로 거절하고, 겹친 코드를 요청 순서대로 모두 알려 준다")
    void rejectCodesInUse() {
        assertThatThrownBy(() -> SkuCodes.validateUnique(
                List.of("A-NV-110", "A-NV-120", "A-NV-130"), Set.of("A-NV-130", "A-NV-110")))
            .isInstanceOfSatisfying(BusinessException.class, e -> {
                assertThat(e.getErrorCode()).isEqualTo(ProductErrorCode.DUPLICATE_SKU_CODE);
                assertThat(e.getDetail()).isEqualTo(
                    "같은 판매자의 다른 옵션과 겹치지 않게 SKU 코드를 넣어 주세요. 겹친 코드는 A-NV-110, A-NV-130입니다.");
            });
    }
}
