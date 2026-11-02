package com.bbcc.kidly.boundedcontext.review.app;

import com.bbcc.kidly.boundedcontext.review.app.port.ReplicaProducts;
import com.bbcc.kidly.boundedcontext.review.app.port.in.ProductReplicaRecorder;
import com.bbcc.kidly.boundedcontext.review.domain.ReplicaProduct;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 상품 복사본 반영. 없으면 만들고, 있으면 늦게 발생한 값일 때만 덮어쓴다. 부르는 쪽 트랜잭션에 합류한다(없으면 새로 연다).
 * 처음 보는 같은 상품이 동시에 오면 저장이 PK(replica_product_pkey) 유일 제약에 막힌다. 저장은 따로 연 트랜잭션에서 하므로
 * 그 위반이면 다시 조회해 늦을 때만 덮어쓰기까지 이 트랜잭션에서 마친다(예외 · 재시도 · DLT 없음, 로그 없음). 그 밖의 위반은 던진다.
 */
@Service
@RequiredArgsConstructor
public class ProductReplicaService implements ProductReplicaRecorder {

    // 상품당 1행을 지키는 PK 제약(마이그레이션 V202611020132)
    static final String PRODUCT_PRIMARY_KEY = "replica_product_pkey";

    private final ReplicaProducts replicaProducts;
    private final ReviewSeparateTransaction separateTransaction;

    @Override
    @Transactional
    public void record(ProductChanged changed) {
        ReplicaProduct.Values values = toValues(changed);
        Optional<ReplicaProduct> stored = replicaProducts.findById(changed.productId());
        if (stored.isPresent()) {
            stored.get().applyIfNewer(values);
            return;
        }
        try {
            separateTransaction.run(() -> replicaProducts.insert(ReplicaProduct.create(values)));
        } catch (DataIntegrityViolationException e) {
            if (!UniqueViolations.isViolationOf(e, PRODUCT_PRIMARY_KEY)) {
                throw e;
            }
            // 처음 보는 상품이 동시에 와서 다른 쪽이 먼저 저장했다. 그 행에 늦을 때만 덮어쓰기 규칙을 그대로 적용한다
            replicaProducts.findById(changed.productId()).orElseThrow(() -> e).applyIfNewer(values);
        }
    }

    private static ReplicaProduct.Values toValues(ProductChanged changed) {
        return new ReplicaProduct.Values(changed.productId(), changed.name(), changed.thumbnailUrl(),
            changed.productStatus(),
            changed.options().stream()
                .map(option -> new ReplicaProduct.OptionState(option.saleStatus(), option.soldOut()))
                .toList(),
            changed.occurredAt());
    }
}
