package com.bbcc.kidly.boundedcontext.wish.out.persistence;

import com.bbcc.kidly.boundedcontext.wish.app.port.WishRequestRepository;
import com.bbcc.kidly.boundedcontext.wish.domain.WishRequest;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.repository.Repository;

/**
 * 조르기 저장소를 Spring Data JPA로 구현한다(#701). 도메인 인터페이스에 선언한 메서드만 노출한다.
 */
@ConditionalOnProperty(name = "kidly.order.wish.enabled", havingValue = "true", matchIfMissing = true)
public interface JpaWishRequestRepository extends Repository<WishRequest, Long>, WishRequestRepository {

    /**
     * 목록은 줄을 함께 읽는다(조르기마다 줄을 따로 읽지 않음).
     */
    @Override
    @EntityGraph(attributePaths = "lines")
    List<WishRequest> findAllByChildIdOrderByIdDesc(Long childId);
}
