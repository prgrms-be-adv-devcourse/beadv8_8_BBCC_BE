package com.bbcc.kidly.boundedcontext.product.out.search;

import com.bbcc.kidly.boundedcontext.product.domain.search.ProductSearchDocument;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.json.JsonMapper;

@RequiredArgsConstructor
class JpaProductSearchDocumentRepositoryImpl {

    // 같은 상품의 이벤트가 동시에 와도 PostgreSQL이 한 문장 안에서 그 행을 잠그므로, 늦게 일어난 이벤트가 남는다.
    // 네이티브 쿼리라 JPA Auditing이 동작하지 않으므로 created_at · updated_at을 직접 넣고, 덮어쓸 때는 created_at을 바꾸지 않는다
    private static final String UPSERT = """
        INSERT INTO product.product_search_document AS d (product_id, name, thumbnail_url, seller_id, category_id,
            status, is_size_free, product_created_at, event_occurred_at, min_price, is_sold_out, attributes,
            created_at, updated_at)
        VALUES (:productId, :name, :thumbnailUrl, :sellerId, :categoryId, :status, :sizeFree, :productCreatedAt,
            :eventOccurredAt, :minPrice, :soldOut, CAST(:attributes AS jsonb), :now, :now)
        ON CONFLICT (product_id) DO UPDATE
        SET name = EXCLUDED.name, thumbnail_url = EXCLUDED.thumbnail_url, seller_id = EXCLUDED.seller_id,
            category_id = EXCLUDED.category_id, status = EXCLUDED.status, is_size_free = EXCLUDED.is_size_free,
            product_created_at = EXCLUDED.product_created_at, event_occurred_at = EXCLUDED.event_occurred_at,
            min_price = EXCLUDED.min_price, is_sold_out = EXCLUDED.is_sold_out, attributes = EXCLUDED.attributes,
            updated_at = EXCLUDED.updated_at
        WHERE d.event_occurred_at < EXCLUDED.event_occurred_at
        """;

    private final EntityManager entityManager;
    private final JsonMapper jsonMapper;

    // 호출한 쪽의 트랜잭션 안에서 실행한다. 바뀐 행이 1개면 반영한 것이다
    public boolean saveIfNewer(ProductSearchDocument document, LocalDateTime now) {
        int changed = entityManager.createNativeQuery(UPSERT)
            .setParameter("productId", document.getProductId())
            .setParameter("name", document.getName())
            .setParameter("thumbnailUrl", document.getThumbnailUrl())
            .setParameter("sellerId", document.getSellerId())
            .setParameter("categoryId", document.getCategoryId())
            .setParameter("status", document.getStatus().name())
            .setParameter("sizeFree", document.isSizeFree())
            .setParameter("productCreatedAt", document.getProductCreatedAt())
            .setParameter("eventOccurredAt", document.getEventOccurredAt())
            .setParameter("minPrice", document.getMinPrice())
            .setParameter("soldOut", document.isSoldOut())
            // jsonb 칸에는 JSON 문자열을 CAST로 넘긴다. Hibernate가 엔티티를 읽을 때 쓰는 JSON 형식과 같다
            .setParameter("attributes", jsonMapper.writeValueAsString(document.getAttributes()))
            .setParameter("now", now)
            .executeUpdate();
        return changed == 1;
    }
}
