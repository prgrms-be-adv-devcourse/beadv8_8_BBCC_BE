package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProductRegisterRequestFixture.options;
import static com.bbcc.kidly.boundedcontext.product.ProductRegisterRequestFixture.productA;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.product.ClothingTopSaver.SavedClothingTop;
import com.bbcc.kidly.global.security.TestMembers;
import com.bbcc.kidly.support.FunctionalTest;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.test.web.servlet.client.RestTestClient;

@FunctionalTest
class ProductRegistrationRepeatApiTest {

    static final String AVAILABLE_AND_RECORDED_QTY = "select i.availableQty, "
        + "(select coalesce(sum(r.deltaQty), 0) from InventoryAdjustmentRecord r "
        + "where r.productOption = i.productOption) "
        + "from Inventory i";

    private static final String PATH = "/api/v1/seller/products";

    private static final long SELLER_ID = 7L;

    private static final int CONCURRENT_REQUESTS = 5;

    @Autowired
    private RestTestClient client;

    @Autowired
    private ClothingTopSaver clothingTopSaver;

    @Autowired
    private EntityManager entityManager;

    private SavedClothingTop saved;

    @BeforeEach
    void saveClothingTop() {
        saved = clothingTopSaver.save();
    }

    static void assertAvailableEqualsRecorded(Object[] row) {
        assertThat(((Number) row[0]).longValue()).isEqualTo(((Number) row[1]).longValue());
    }

    @Test
    @DisplayName("INV-02 · INV-19 같은 등록 요청을 두 번 보내면 상품이 둘 생기고, 옵션마다 재고 행이 하나씩이며 수량과 기록 합이 같다")
    void registerSameRequestTwice() {
        Map<String, Object> body = withoutSkuCodes(productA(saved));

        assertThat(register(body)).isEqualTo(HttpStatus.CREATED);
        assertThat(register(body)).isEqualTo(HttpStatus.CREATED);

        expectOneSetPerProduct(2);
    }

    @Test
    @DisplayName("INV-02 · INV-19 같은 등록 요청을 동시에 보내도 모두 성공하고, 옵션마다 재고 행이 하나씩이며 수량과 기록 합이 같다")
    void registerSameRequestConcurrently() throws Exception {
        Map<String, Object> body = withoutSkuCodes(productA(saved));
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_REQUESTS);
        List<Future<HttpStatusCode>> results = new ArrayList<>();
        try {
            for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
                results.add(executor.submit(() -> {
                    start.await(10, TimeUnit.SECONDS);
                    return register(body);
                }));
            }
            start.countDown();
            for (Future<HttpStatusCode> result : results) {
                assertThat(result.get(30, TimeUnit.SECONDS)).isEqualTo(HttpStatus.CREATED);
            }
        } finally {
            executor.shutdownNow();
        }

        expectOneSetPerProduct(CONCURRENT_REQUESTS);
    }

    private void expectOneSetPerProduct(int products) {
        assertThat(count("select count(p) from Product p")).isEqualTo(products);
        assertThat(count("select count(o) from ProductOption o")).isEqualTo(products * 2L);
        assertThat(count("select count(distinct i.productOption) from Inventory i")).isEqualTo(products * 2L);
        assertThat(count("select count(i) from Inventory i")).isEqualTo(products * 2L);
        assertThat(count("select count(r) from InventoryAdjustmentRecord r")).isEqualTo(products);
        assertThat(entityManager.createQuery(AVAILABLE_AND_RECORDED_QTY, Object[].class).getResultList())
            .allSatisfy(ProductRegistrationRepeatApiTest::assertAvailableEqualsRecorded);
    }

    private static Map<String, Object> withoutSkuCodes(Map<String, Object> body) {
        options(body).forEach(option -> option.put("skuCode", null));
        return body;
    }

    private HttpStatusCode register(Map<String, Object> body) {
        return client.post().uri(PATH)
            .headers(TestMembers.seller(SELLER_ID))
            .body(body)
            .exchange()
            .returnResult()
            .getStatus();
    }

    private long count(String jpql) {
        return entityManager.createQuery(jpql, Long.class).getSingleResult();
    }
}
