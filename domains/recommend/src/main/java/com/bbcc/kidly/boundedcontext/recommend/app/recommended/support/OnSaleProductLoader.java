package com.bbcc.kidly.boundedcontext.recommend.app.recommended.support;

import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaProduct;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaProductRepository;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.ReplicaProductSnapshot;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * DB의 상품 복사본을 읽어 스냅숏으로 옮겨 돌려준다.
 * 지연 로딩하는 컬렉션을 스냅숏으로 옮기는 동안 세션이 열려 있어야 하므로 메서드마다 읽기 전용 트랜잭션으로 읽는다.
 * 요청의 트랜잭션 안에서 불리면 그 트랜잭션에 합류하고, 트랜잭션이 없을 때(앱이 뜰 때)만 새로 연다.
 */
@Component
@RequiredArgsConstructor
public class OnSaleProductLoader {

    private final RecommendReplicaProductRepository replicaProductRepository;

    @Transactional(readOnly = true)
    public List<ReplicaProductSnapshot> loadAllOnSale() {
        return toSnapshots(replicaProductRepository.findAllOnSale(null));
    }

    // 판매가 끝난 상품을 보관한 값에서 빼려면 알아야 하므로 판매 상태와 상관없이 읽는다
    @Transactional(readOnly = true)
    public List<ReplicaProductSnapshot> loadChangedAfter(LocalDateTime since) {
        return toSnapshots(replicaProductRepository.findAllChangedAfter(since));
    }

    @Transactional(readOnly = true)
    public long countOnSale() {
        return replicaProductRepository.countOnSale();
    }

    private List<ReplicaProductSnapshot> toSnapshots(List<RecommendReplicaProduct> products) {
        List<ReplicaProductSnapshot> snapshots = new ArrayList<>();
        for (RecommendReplicaProduct product : products) {
            snapshots.add(ReplicaProductSnapshot.from(product));
        }
        return snapshots;
    }
}
