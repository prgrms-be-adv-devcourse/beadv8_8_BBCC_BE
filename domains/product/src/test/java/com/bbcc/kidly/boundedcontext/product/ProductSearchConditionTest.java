package com.bbcc.kidly.boundedcontext.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.product.app.port.search.ProductSearchCondition;
import com.bbcc.kidly.boundedcontext.product.app.port.search.ProductSearchSort;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProductSearchConditionTest {

    @Test
    @DisplayName("정렬이 없으면 상품 목록 조회 조건을 만들지 않는다")
    void rejectWithoutSort() {
        assertThatThrownBy(() -> new ProductSearchCondition(null, null, Map.of(), null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("sort는 필수입니다");
    }

    @Test
    @DisplayName("항목 값 조건이 null이면 고른 항목 값이 없는 조건으로 받는다")
    void treatNullAttributesAsEmpty() {
        ProductSearchCondition condition = new ProductSearchCondition(null, null, null, ProductSearchSort.NEWEST);

        assertThat(condition.attributeValueIds()).isEmpty();
    }
}
