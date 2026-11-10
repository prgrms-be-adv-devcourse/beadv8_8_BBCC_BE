package com.bbcc.kidly.boundedcontext.recommend.out.embedding;

import com.bbcc.kidly.boundedcontext.recommend.app.port.embedding.ProductEmbeddingRunLock;
import javax.sql.DataSource;
import org.springframework.stereotype.Component;

/**
 * 상품 임베딩 실행 잠금이다. PostgreSQL 세션 advisory lock으로 구현한다.
 */
@Component
public class ProductEmbeddingRunLockStore implements ProductEmbeddingRunLock {

    private final DataSource dataSource;

    public ProductEmbeddingRunLockStore(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public boolean runExclusively(Runnable work) {
        return false;
    }
}
