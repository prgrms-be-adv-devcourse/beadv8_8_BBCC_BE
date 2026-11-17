package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProductRegisterRequestFixture.productA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryRepository;
import com.bbcc.kidly.global.security.TestMembers;
import com.bbcc.kidly.support.FunctionalTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * 상품은 IDENTITY 키라 save에서 바로 INSERT되므로, 첫 재고 저장에서 실패해도 상품 · 옵션 행은 이미 DB에 들어간 뒤다.
 */
@FunctionalTest
class ProductRegistrationRollbackApiTest {

    private static final long SELLER_ID = 7L;

    @Autowired
    private RestTestClient client;

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
            .headers(TestMembers.seller(SELLER_ID))
            .body(productA(saved))
            .exchange()
            .expectStatus().is5xxServerError();

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
