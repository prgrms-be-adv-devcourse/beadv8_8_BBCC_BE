package com.bbcc.kidly.boundedcontext.review.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.review.app.port.ReplicaProducts;
import com.bbcc.kidly.boundedcontext.review.domain.ReplicaProduct;
import com.bbcc.kidly.boundedcontext.support.IntegrationTest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 상품 복사본 저장은 insert 의미다: 같은 상품 행이 이미 커밋돼 있으면(조회와 저장 사이에 다른 수신이 저장한 경우) 덮어쓰지 않고
 * PK(replica_product_pkey) 위반으로 실패해야 한다. 그래야 받는 쪽이 다시 조회해 늦을 때만 덮어쓰기 규칙을 적용할 수 있다.
 */
@IntegrationTest
class JpaReplicaProductRepositoryIntegrationTest {

    private static final long PRODUCT_ID = 31L;
    private static final LocalDateTime NEWER = LocalDateTime.now().minusMinutes(1);

    @Autowired
    private ReplicaProducts replicaProducts;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("처음 보는 상품은 새 행으로 저장된다")
    void insertsNew() {
        transactionTemplate.executeWithoutResult(status -> replicaProducts.insert(ReplicaProduct.create(
            new ReplicaProduct.Values(PRODUCT_ID, "새 상품", null, "ON_SALE", List.of(), NEWER))));

        assertThat(row()).singleElement().satisfies(r -> assertThat(r).containsEntry("name", "새 상품"));
    }

    @Test
    @DisplayName("더 늦은 행이 이미 커밋된 상품에 이른 값을 저장하면 UPDATE가 아니라 PK 위반이고 행은 그대로다")
    void insertOverCommittedRowViolatesPrimaryKey() {
        jdbcTemplate.update("insert into community.replica_product (product_id, name, thumbnail_url, display_status, "
            + "source_occurred_at, created_at, updated_at) values (?, '늦은 값', null, 'ON_SALE', ?, now(), now())",
            PRODUCT_ID, NEWER);
        List<Map<String, Object>> before = row();

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> replicaProducts.insert(
            ReplicaProduct.create(new ReplicaProduct.Values(PRODUCT_ID, "이른 값", null, "STOPPED", List.of(),
                NEWER.minusMinutes(1))))))
            .isInstanceOf(DataIntegrityViolationException.class)
            .satisfies(e -> assertThat(e.getCause()).isInstanceOfSatisfying(ConstraintViolationException.class,
                violation -> assertThat(violation.getConstraintName()).isEqualTo("replica_product_pkey")));
        assertThat(row()).isEqualTo(before);
    }

    private List<Map<String, Object>> row() {
        return jdbcTemplate.queryForList("select name, display_status, source_occurred_at, updated_at "
            + "from community.replica_product where product_id = ?", PRODUCT_ID);
    }
}
