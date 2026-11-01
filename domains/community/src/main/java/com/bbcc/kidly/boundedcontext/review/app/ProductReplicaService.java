package com.bbcc.kidly.boundedcontext.review.app;

import com.bbcc.kidly.boundedcontext.review.app.port.ReplicaProducts;
import com.bbcc.kidly.boundedcontext.review.app.port.in.ProductReplicaRecorder;
import com.bbcc.kidly.boundedcontext.review.domain.ReplicaProduct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 상품 복사본 반영. 없으면 만들고, 있으면 늦게 발생한 값일 때만 덮어쓴다. 부르는 쪽 트랜잭션에 합류한다(없으면 새로 연다).
 */
@Service
@RequiredArgsConstructor
public class ProductReplicaService implements ProductReplicaRecorder {

    private final ReplicaProducts replicaProducts;

    @Override
    @Transactional
    public void record(ProductChanged changed) {
        ReplicaProduct.Values values = toValues(changed);
        replicaProducts.findById(changed.productId()).ifPresentOrElse(
            replica -> replica.applyIfNewer(values),
            () -> replicaProducts.save(ReplicaProduct.create(values)));
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
