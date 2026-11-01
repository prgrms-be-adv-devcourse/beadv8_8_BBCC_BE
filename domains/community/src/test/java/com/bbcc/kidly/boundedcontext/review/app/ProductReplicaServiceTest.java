package com.bbcc.kidly.boundedcontext.review.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.review.app.port.ReplicaProducts;
import com.bbcc.kidly.boundedcontext.review.app.port.in.ProductReplicaRecorder.Option;
import com.bbcc.kidly.boundedcontext.review.app.port.in.ProductReplicaRecorder.ProductChanged;
import com.bbcc.kidly.boundedcontext.review.domain.DisplayStatus;
import com.bbcc.kidly.boundedcontext.review.domain.ReplicaProduct;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProductReplicaServiceTest {

    private static final LocalDateTime OCCURRED_AT = LocalDateTime.now().minusHours(1);

    private final FakeReplicaProducts replicas = new FakeReplicaProducts();
    private final ProductReplicaService service = new ProductReplicaService(replicas);

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

    private static ProductChanged changed(String name, String status, LocalDateTime occurredAt) {
        return new ProductChanged(1L, name, null, status, List.of(new Option("ON_SALE", false)), occurredAt);
    }

    private static final class FakeReplicaProducts implements ReplicaProducts {

        private final Map<Long, ReplicaProduct> rows = new HashMap<>();
        private int saves;

        @Override
        public Optional<ReplicaProduct> findById(Long productId) {
            return Optional.ofNullable(rows.get(productId));
        }

        @Override
        public ReplicaProduct save(ReplicaProduct replica) {
            saves++;
            rows.put(replica.getProductId(), replica);
            return replica;
        }
    }
}
