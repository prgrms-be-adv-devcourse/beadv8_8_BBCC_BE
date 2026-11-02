package com.bbcc.kidly.boundedcontext.review.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.review.app.port.ReplicaProducts;
import com.bbcc.kidly.boundedcontext.review.app.port.in.ProductReplicaRecorder.Option;
import com.bbcc.kidly.boundedcontext.review.app.port.in.ProductReplicaRecorder.ProductChanged;
import com.bbcc.kidly.boundedcontext.review.domain.DisplayStatus;
import com.bbcc.kidly.boundedcontext.review.domain.ReplicaProduct;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class ProductReplicaServiceTest {

    private static final LocalDateTime OCCURRED_AT = LocalDateTime.now().minusHours(1);

    private final FakeReplicaProducts replicas = new FakeReplicaProducts();
    private final ProductReplicaService service =
        new ProductReplicaService(replicas, new ReviewSeparateTransaction());

    @Test
    @DisplayName("처음 받은 상품은 복사본을 만든다")
    void createsFirst() {
        service.record(changed("상품 A", "ON_SALE", OCCURRED_AT));

        assertThat(replicas.findById(1L)).get().satisfies(replica -> {
            assertThat(replica.getName()).isEqualTo("상품 A");
            assertThat(replica.getDisplayStatus()).isEqualTo(DisplayStatus.ON_SALE);
        });
        assertThat(replicas.saves).isEqualTo(1);
    }

    @Test
    @DisplayName("늦게 발생한 이벤트는 저장된 복사본을 덮어쓴다")
    void updatesNewer() {
        service.record(changed("상품 A", "ON_SALE", OCCURRED_AT));

        service.record(changed("상품 B", "STOPPED", OCCURRED_AT.plusSeconds(1)));

        assertThat(replicas.findById(1L)).get().satisfies(replica -> {
            assertThat(replica.getName()).isEqualTo("상품 B");
            assertThat(replica.getDisplayStatus()).isEqualTo(DisplayStatus.SALE_ENDED);
        });
    }

    @Test
    @DisplayName("같거나 이른 이벤트는 무시하고 새로 만들지 않는다")
    void ignoresSameOrOlder() {
        service.record(changed("상품 A", "ON_SALE", OCCURRED_AT));

        service.record(changed("상품 B", "STOPPED", OCCURRED_AT));
        service.record(changed("상품 C", "STOPPED", OCCURRED_AT.minusSeconds(1)));

        assertThat(replicas.findById(1L)).get().extracting(ReplicaProduct::getName).isEqualTo("상품 A");
        assertThat(replicas.saves).isEqualTo(1);
    }

    @Test
    @DisplayName("조회 뒤 저장이 상품 PK 유일 제약에 막혀도(동시 수신) 다시 조회해 늦은 값이면 덮어쓰고 예외 없이 끝낸다")
    void concurrentFirstInsertAppliesNewer() {
        replicas.insertedByOther = ReplicaProduct.create(values("상품 다른쪽", OCCURRED_AT));

        service.record(changed("상품 B", "STOPPED", OCCURRED_AT.plusSeconds(1)));

        assertThat(replicas.findById(1L)).get().extracting(ReplicaProduct::getName).isEqualTo("상품 B");
    }

    @Test
    @DisplayName("동시 수신으로 먼저 저장된 값이 더 늦으면 그 값을 유지한다")
    void concurrentFirstInsertKeepsNewerStored() {
        replicas.insertedByOther = ReplicaProduct.create(values("상품 다른쪽", OCCURRED_AT.plusSeconds(1)));

        service.record(changed("상품 B", "STOPPED", OCCURRED_AT));

        assertThat(replicas.findById(1L)).get().extracting(ReplicaProduct::getName).isEqualTo("상품 다른쪽");
    }

    @Test
    @DisplayName("상품 PK가 아닌 위반(NOT NULL 등)은 그대로 던진다")
    void otherViolationPropagates() {
        replicas.insertedByOther = ReplicaProduct.create(values("상품 다른쪽", OCCURRED_AT));
        replicas.violation = violation("23502", null);

        assertThatThrownBy(() -> service.record(changed("상품 B", "STOPPED", OCCURRED_AT.plusSeconds(1))))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    private static ReplicaProduct.Values values(String name, LocalDateTime occurredAt) {
        return new ReplicaProduct.Values(1L, name, null, "ON_SALE", List.of(), occurredAt);
    }

    private static DataIntegrityViolationException violation(String sqlState, String constraint) {
        return new DataIntegrityViolationException("could not execute statement",
            new ConstraintViolationException("could not execute statement", new SQLException("violation", sqlState),
                constraint));
    }

    private static ProductChanged changed(String name, String status, LocalDateTime occurredAt) {
        return new ProductChanged(1L, name, null, status, List.of(new Option("ON_SALE", false)), occurredAt);
    }

    private static final class FakeReplicaProducts implements ReplicaProducts {

        private final Map<Long, ReplicaProduct> rows = new HashMap<>();
        private int saves;
        // 조회와 저장 사이에 다른 수신이 먼저 저장한 행. 저장하면 이 행이 생기고 위반이 난다
        private ReplicaProduct insertedByOther;
        private DataIntegrityViolationException violation =
            violation("23505", ProductReplicaService.PRODUCT_PRIMARY_KEY);

        @Override
        public Optional<ReplicaProduct> findById(Long productId) {
            return Optional.ofNullable(rows.get(productId));
        }

        @Override
        public ReplicaProduct insert(ReplicaProduct replica) {
            saves++;
            if (insertedByOther != null) {
                rows.put(insertedByOther.getProductId(), insertedByOther);
                insertedByOther = null;
                throw violation;
            }
            rows.put(replica.getProductId(), replica);
            return replica;
        }
    }
}
