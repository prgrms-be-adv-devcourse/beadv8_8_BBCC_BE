package com.bbcc.kidly.boundedcontext.wish.out.persistence;

import com.bbcc.kidly.boundedcontext.wish.app.port.WishItemRepository;
import com.bbcc.kidly.boundedcontext.wish.domain.WishItem;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.repository.Repository;

/**
 * 조르기 줄 저장소를 Spring Data JPA로 구현한다(#640). 도메인 인터페이스에 선언한 메서드만 노출한다.
 */
@ConditionalOnProperty(name = "kidly.order.wish.enabled", havingValue = "true", matchIfMissing = true)
public interface JpaWishItemRepository extends Repository<WishItem, Long>, WishItemRepository {
}
