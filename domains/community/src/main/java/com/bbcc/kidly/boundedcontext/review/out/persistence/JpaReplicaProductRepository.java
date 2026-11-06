package com.bbcc.kidly.boundedcontext.review.out.persistence;

import com.bbcc.kidly.boundedcontext.review.app.port.ReplicaProductStats;
import com.bbcc.kidly.boundedcontext.review.app.port.ReplicaProducts;
import com.bbcc.kidly.boundedcontext.review.domain.ReplicaProduct;
import com.bbcc.kidly.boundedcontext.review.out.store.ReplicaProductInsert;
import com.bbcc.kidly.boundedcontext.review.out.store.ReplicaProductLock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * 상품 복사본 저장소. 새 행 저장(insert)은 persist만 하는 조각(out.store.ReplicaProductInsert)이 맡는다.
 */
public interface JpaReplicaProductRepository extends JpaRepository<ReplicaProduct, Long>, ReplicaProducts,
    ReplicaProductStats, ReplicaProductInsert, ReplicaProductLock {

    @Override
    @Query("select p.displayStatus as status, count(p) as total from CommunityReplicaProduct p"
        + " group by p.displayStatus")
    List<DisplayStatusCountRow> countByDisplayStatus();

    @Override
    @Query("select max(p.updatedAt) from CommunityReplicaProduct p")
    LocalDateTime findLastUpdatedAt();

    @Override
    @Query("select max(p.sourceOccurredAt) from CommunityReplicaProduct p")
    LocalDateTime findLastSourceOccurredAt();
}
