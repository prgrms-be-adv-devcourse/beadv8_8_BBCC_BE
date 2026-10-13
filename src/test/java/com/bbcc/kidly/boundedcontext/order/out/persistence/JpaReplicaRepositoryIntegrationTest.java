package com.bbcc.kidly.boundedcontext.order.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.order.app.port.ChildReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.ProductReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaChild;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaProduct;
import com.bbcc.kidly.support.IntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class JpaReplicaRepositoryIntegrationTest {

    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 10, 13, 9, 0);

    @Autowired
    private ProductReplicaRepository productReplicaRepository;

    @Autowired
    private ChildReplicaRepository childReplicaRepository;

    @Test
    @DisplayName("상품 복사본을 옵션 id · 옵션 id 목록 · 상품 id로 찾는다")
    void findsProductReplica() {
        productReplicaRepository.save(ReplicaProduct.create(product(1L, 1L)));
        productReplicaRepository.save(ReplicaProduct.create(product(2L, 1L)));
        productReplicaRepository.save(ReplicaProduct.create(product(3L, 2L)));

        assertThat(productReplicaRepository.findByOptionId(1L)).get()
            .extracting(ReplicaProduct::getProductName).isEqualTo("상품 A");
        assertThat(productReplicaRepository.findByOptionId(9L)).isEmpty();
        assertThat(productReplicaRepository.findAllByOptionIdIn(List.of(1L, 3L, 9L)))
            .extracting(ReplicaProduct::getOptionId).containsExactlyInAnyOrder(1L, 3L);
        assertThat(productReplicaRepository.findAllByProductId(1L))
            .extracting(ReplicaProduct::getOptionId).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    @DisplayName("자녀 복사본을 자녀 id로 찾는다")
    void findsChildReplica() {
        childReplicaRepository.save(ReplicaChild.create(new ReplicaChild.Values(
            1L, 1L, null, new BigDecimal("115.0"), new BigDecimal("20.0"), "NORMAL", false, OCCURRED_AT)));

        assertThat(childReplicaRepository.findByChildId(1L)).get()
            .extracting(ReplicaChild::getMemberId).isEqualTo(1L);
        assertThat(childReplicaRepository.findByChildId(9L)).isEmpty();
    }

    private static ReplicaProduct.Values product(Long optionId, Long productId) {
        return new ReplicaProduct.Values(optionId, productId, 2L, "상품 A", "110", 25_000L, 1L, "ON_SALE", false,
            OCCURRED_AT);
    }
}
