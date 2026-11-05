package com.bbcc.kidly.boundedcontext.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.notification.app.ProductNameService;
import com.bbcc.kidly.support.IntegrationTest;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class ProductNameServiceIntegrationTest {

    private static final Long PRODUCT_ID = 10L;
    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 11, 5, 9, 0);

    @Autowired
    private ProductNameService productNameService;

    @Test
    @DisplayName("처음 받은 상품 이름을 저장하고 상품 ID로 찾는다")
    void saveAndFind() {
        productNameService.save(PRODUCT_ID, "곰돌이 티셔츠", OCCURRED_AT);

        assertThat(productNameService.findName(PRODUCT_ID)).contains("곰돌이 티셔츠");
    }

    @Test
    @DisplayName("더 늦게 발생한 이벤트면 덮어쓰고, 옛 이벤트는 무시한다")
    void overwriteOnlyNewer() {
        productNameService.save(PRODUCT_ID, "곰돌이 티셔츠", OCCURRED_AT);

        productNameService.save(PRODUCT_ID, "곰돌이 반팔 티셔츠", OCCURRED_AT.plusMinutes(1));
        productNameService.save(PRODUCT_ID, "옛 이름", OCCURRED_AT.plusSeconds(30));

        assertThat(productNameService.findName(PRODUCT_ID)).contains("곰돌이 반팔 티셔츠");
    }

    @Test
    @DisplayName("받지 못한 상품이면 비어 있다")
    void notFound() {
        assertThat(productNameService.findName(PRODUCT_ID)).isEmpty();
    }
}
