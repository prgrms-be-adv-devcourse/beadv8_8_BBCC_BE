package com.bbcc.kidly.boundedcontext.childprofile.out.outbox;

import com.bbcc.kidly.boundedcontext.childprofile.domain.outbox.ChildProfileOutboxMessage;
import com.bbcc.kidly.boundedcontext.childprofile.domain.outbox.ChildProfileOutboxRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 자녀 프로필 아웃박스 메시지를 JPA로 저장 · 조회 · 삭제한다.
 */
public interface JpaChildProfileOutboxRepository
    extends JpaRepository<ChildProfileOutboxMessage, Long>, ChildProfileOutboxRepository {

    // JpaRepository와 ChildProfileOutboxRepository의 save를 둘 다 물려받아, 이 타입으로 save를 부르면 컴파일러가 하나를 고르지 못한다.
    // 여기서 다시 선언해 하나로 정한다. 실행은 그대로 Spring Data의 기본 save가 맡는다.
    @Override
    ChildProfileOutboxMessage save(ChildProfileOutboxMessage message);

    // 같은 이벤트를 다시 발행했는지 JpaChildProfileEventOutbox가 확인한다. app은 쓰지 않으므로 domain 요청서에 두지 않는다
    boolean existsByEventId(UUID eventId);

    // PostgreSQL 트랜잭션 advisory lock. 키는 이 릴레이 전용 문자열의 해시라 다른 잠금과 겹치지 않는다.
    // 세션 잠금(pg_try_advisory_lock)은 쓰지 않는다. 트랜잭션이 끝나면 연결을 풀에 돌려주는 설정이라 같은 연결로 풀 수 없다
    @Override
    @Query(value = "select pg_try_advisory_xact_lock(hashtext('kidly.childprofile.outbox-relay'))", nativeQuery = true)
    boolean tryLockRelay();

    List<ChildProfileOutboxMessage> findBySentAtIsNullOrderByIdAsc(Limit limit);

    @Override
    default List<ChildProfileOutboxMessage> findUnsent(int limit) {
        return findBySentAtIsNullOrderByIdAsc(Limit.of(limit));
    }

    // sent_at이 null인 행은 비교 결과가 참이 아니므로 지우지 않는다
    @Override
    @Modifying
    @Query("delete from ChildProfileOutboxMessage m where m.sentAt < :cutoff")
    int deleteSentBefore(@Param("cutoff") LocalDateTime cutoff);
}
