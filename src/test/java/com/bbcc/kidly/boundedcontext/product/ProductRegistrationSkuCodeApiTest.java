package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProductRegisterRequestFixture.productA;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.product.ClothingTopSaver.SavedClothingTop;
import com.bbcc.kidly.support.FunctionalTest;
import com.bbcc.kidly.support.TestTokens;
import jakarta.persistence.EntityManager;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * 상품을 등록할 때 옵션의 SKU 코드가 같은 판매자의 다른 옵션과 겹치는지 HTTP 요청부터 응답까지 확인한다.
 * 상품 A의 요청은 네이비 110 옵션에 SKU 코드 A-NV-110을 넣는다.
 */
@FunctionalTest
class ProductRegistrationSkuCodeApiTest {

    private static final String PATH = "/api/v1/seller/products";

    private static final long SELLER_ID = 7L;

    @Autowired
    private RestTestClient client;

    @Autowired
    private TestTokens tokens;

    @Autowired
    private ClothingTopSaver clothingTopSaver;

    // 실패한 요청이 아무것도 저장하지 않았는지 확인할 때 쓴다
    @Autowired
    private EntityManager entityManager;

    private SavedClothingTop saved;

    @BeforeEach
    void saveClothingTop() {
        saved = clothingTopSaver.save();
    }

    @Test
    @DisplayName("OPT-07 같은 판매자가 이미 쓰는 SKU 코드로 다른 상품을 등록하면 409를 받고 아무것도 저장하지 않는다")
    void rejectSkuCodeInUse() {
        register(SELLER_ID, productA(saved)).expectStatus().isCreated();
        Map<String, Object> body = productA(saved);
        body.put("name", "상품 B");

        register(SELLER_ID, body)
            .expectStatus().isEqualTo(HttpStatus.CONFLICT)
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.type").isEqualTo("about:blank")
            .jsonPath("$.title").isEqualTo("이미 쓰는 SKU 코드입니다")
            .jsonPath("$.status").isEqualTo(409)
            .jsonPath("$.detail").isEqualTo(
                "같은 판매자의 다른 옵션과 겹치지 않게 SKU 코드를 넣어 주세요. 겹친 코드는 A-NV-110입니다.")
            .jsonPath("$.instance").isEqualTo(PATH);
        assertThat(count("Product")).isEqualTo(1);
        assertThat(count("ProductOption")).isEqualTo(2);
        assertThat(count("Inventory")).isEqualTo(2);
    }

    private RestTestClient.ResponseSpec register(long sellerId, Map<String, Object> body) {
        return client.post().uri(PATH)
            .headers(headers -> headers.setBearerAuth(tokens.seller(sellerId)))
            .body(body)
            .exchange();
    }

    private long count(String entityName) {
        return entityManager.createQuery("select count(e) from " + entityName + " e", Long.class).getSingleResult();
    }
}
