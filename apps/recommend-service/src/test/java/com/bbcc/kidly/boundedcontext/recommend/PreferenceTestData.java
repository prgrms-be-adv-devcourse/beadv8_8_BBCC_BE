package com.bbcc.kidly.boundedcontext.recommend;

import com.bbcc.kidly.boundedcontext.recommend.domain.activity.ActivityType;
import com.bbcc.kidly.boundedcontext.recommend.domain.embedding.EmbeddingVector;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 선호 저장 · 읽기 통합 테스트가 함께 쓰는 값을 만들고 표에 넣는다.
 */
final class PreferenceTestData {

    private static final AtomicLong ORDER_ITEM_IDS = new AtomicLong();

    static EmbeddingVector vectorOf(float seed) {
        float[] values = new float[EmbeddingVector.DIMENSIONS];
        for (int i = 0; i < values.length; i++) {
            values[i] = seed + i / 1_000f;
        }
        return new EmbeddingVector(values);
    }

    /**
     * 활동 기록 한 줄을 넣는다. 카테고리 · 브랜드 · 상품 · 장바구니 줄 ID는 null일 수 있다.
     */
    static void insertActivity(JdbcTemplate jdbc, ActivityType type, Long memberId, Long childProfileId,
        LocalDateTime occurredAt, Long productId, Long categoryId, String brand, Long cartItemId) {
        jdbc.update("""
            INSERT INTO recommend.activity_record (event_id, type, member_id, child_profile_id, occurred_at,
                product_id, category_id, brand, cart_item_id, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now())
            """, UUID.randomUUID(), type.name(), memberId, childProfileId, occurredAt, productId, categoryId, brand,
            cartItemId);
    }

    static void insertPurchase(JdbcTemplate jdbc, Long childProfileId, Long productId, LocalDateTime confirmedAt) {
        jdbc.update("""
            INSERT INTO recommend.purchase_record (order_item_id, order_id, member_id, child_profile_id, product_id,
                product_option_id, size, confirmed_at, created_at, updated_at)
            VALUES (?, 1, 1, ?, ?, 1, 'M', ?, now(), now())
            """, ORDER_ITEM_IDS.incrementAndGet(), childProfileId, productId, confirmedAt);
    }

    /**
     * 상품 복사본 한 줄을 넣는다. 벡터가 null이면 벡터 없이 넣는다.
     */
    static void insertProduct(JdbcTemplate jdbc, Long productId, Long categoryId, String brand,
        EmbeddingVector embedding) {
        String vectorText = null;
        if (embedding != null) {
            vectorText = Arrays.toString(embedding.values());
        }
        jdbc.update("""
            INSERT INTO recommend.replica_product (product_id, seller_id, name, status, category_id, brand,
                is_size_free, product_registered_at, event_occurred_at, embedding, created_at, updated_at)
            VALUES (?, 1, '상품', 'ON_SALE', ?, ?, false, now(), now(), CAST(? AS recommend.vector), now(), now())
            """, productId, categoryId, brand, vectorText);
    }

    private PreferenceTestData() {
    }
}
