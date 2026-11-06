package com.bbcc.kidly.boundedcontext.wish.out.persistence;

import com.bbcc.kidly.boundedcontext.wish.app.port.WishPurchaseRepository;
import com.bbcc.kidly.boundedcontext.wish.domain.WishPurchase;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.repository.Repository;

/**
 * 조르기로 산 것 저장소를 Spring Data JPA로 구현한다(#999). 도메인 인터페이스에 선언한 메서드만 노출한다.
 */
@ConditionalOnProperty(name = "kidly.order.wish.enabled", havingValue = "true", matchIfMissing = true)
public interface JpaWishPurchaseRepository extends Repository<WishPurchase, Long>, WishPurchaseRepository {
}
