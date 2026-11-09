package com.bbcc.kidly.boundedcontext.wish.out.persistence;

import com.bbcc.kidly.boundedcontext.wish.app.port.WishRequestRepository;
import com.bbcc.kidly.boundedcontext.wish.domain.PrincipalType;
import com.bbcc.kidly.boundedcontext.wish.domain.WishRequest;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/**
 * 조르기 저장소를 Spring Data JPA로 구현한다(#701 · #999). 도메인 인터페이스에 선언한 메서드만 노출한다.
 */
@ConditionalOnProperty(name = "kidly.order.wish.enabled", havingValue = "true", matchIfMissing = true)
public interface JpaWishRequestRepository extends Repository<WishRequest, Long>, WishRequestRepository {

    /**
     * 목록은 줄을 함께 읽는다(조르기마다 줄을 따로 읽지 않음).
     */
    @Override
    @EntityGraph(attributePaths = "lines")
    List<WishRequest> findAllByRequestedByTypeAndRequestedByIdInOrderByIdDesc(PrincipalType type,
        Collection<Long> requestedByIds);

    @Override
    @Query("""
        select r.id from WishRequest r
        where r.status = :status and r.createdAt <= :sentBefore and r.id > :afterId
        order by r.id asc
        """)
    List<Long> findOverdueRequestIds(WishRequest.Status status, LocalDateTime sentBefore, Long afterId, Limit limit);
}
