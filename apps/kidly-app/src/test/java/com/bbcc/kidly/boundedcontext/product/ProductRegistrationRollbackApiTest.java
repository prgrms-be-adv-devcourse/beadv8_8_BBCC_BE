package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProductRegisterRequestFixture.productA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryRepository;
import com.bbcc.kidly.support.FunctionalTest;
import com.bbcc.kidly.support.TestTokens;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * 상품을 저장한 뒤 재고를 저장하다 실패해도 등록 전체가 롤백되는지 확인한다.
 * 정상 입력으로는 저장 뒤에 실패할 수 없어서 재고 리포지토리를 spy로 바꿔 재고 저장에서 실패시킨다.
 * 상품은 IDENTITY 키라 save에서 바로 INSERT되므로, 첫 재고 저장에서 실패해도 상품 · 옵션 행은 이미 DB에 들어간 뒤다.
 * spy 설정 때문에 스프링 컨텍스트가 따로 뜨므로, 다른 등록 테스트와 섞이지 않게 이 클래스에 이 테스트만 둔다.
 */
@FunctionalTest
class ProductRegistrationRollbackApiTest {

    private static final long SELLER_ID = 7L;

    @Autowired
    private RestTestClient client;

    @Autowired
    private TestTokens tokens;

    @Autowired
    private ClothingTopSaver clothingTopSaver;

    @Autowired
    private EntityManager entityManager;

    @MockitoSpyBean
    private InventoryRepository inventoryRepository;

    @Test
    @DisplayName("상품을 저장한 뒤 재고를 저장하다 실패하면 상품 · 옵션 · 재고 · 재고 조정 기록이 하나도 남지 않는다")
    void rollBackWhenInventoryFailsAfterProductSaved() {
        ClothingTopSaver.SavedClothingTop saved = clothingTopSaver.save();
        doThrow(new IllegalStateException("재고 저장을 일부러 실패시킨다"))
            .when(inventoryRepository).saveOpening(any());

        client.post().uri("/api/v1/seller/products")
            .headers(headers -> headers.setBearerAuth(tokens.seller(SELLER_ID)))
            .body(productA(saved))
            .exchange()
            .expectStatus().is5xxServerError();

        // 실패가 상품 저장 뒤 재고 저장에서 났는지 확인한다. 그 전에 실패했다면 롤백을 확인한 것이 아니다
        verify(inventoryRepository).saveOpening(any());
        assertThat(count("Product")).isZero();
        assertThat(count("ProductOption")).isZero();
        assertThat(count("ProductInfoValue")).isZero();
        assertThat(count("SizeSpec")).isZero();
        assertThat(count("Inventory")).isZero();
        assertThat(count("InventoryAdjustmentRecord")).isZero();
    }

    private long count(String entityName) {
        return entityManager.createQuery("select count(e) from " + entityName + " e", Long.class).getSingleResult();
    }
}
