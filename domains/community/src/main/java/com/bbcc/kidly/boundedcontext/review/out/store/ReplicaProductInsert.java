package com.bbcc.kidly.boundedcontext.review.out.store;

import com.bbcc.kidly.boundedcontext.review.domain.ReplicaProduct;

/**
 * 상품 복사본을 새 행으로만 넣는다(Spring Data 저장소 조각). 상품 ID를 직접 넣는 엔티티라 Spring Data의 save는 merge가 되어,
 * 조회와 저장 사이에 다른 수신이 같은 상품을 커밋했으면 그 행을 덮어쓴다. 여기서는 persist만 해서 그때 PK 위반이 난다.
 */
public interface ReplicaProductInsert {

    ReplicaProduct insert(ReplicaProduct replica);
}
