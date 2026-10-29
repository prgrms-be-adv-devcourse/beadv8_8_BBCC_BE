package com.bbcc.kidly.boundedcontext.product.domain.outbox;

import java.util.List;

public interface ProductOutboxRepository {

    ProductOutboxMessage save(ProductOutboxMessage message);

    /**
     * 아직 보내지 않은 메시지를 id 오름차순으로 최대 limit건 찾는다.
     */
    List<ProductOutboxMessage> findUnsent(int limit);
}
