package com.bbcc.kidly.boundedcontext.childprofile.domain.outbox;

import java.time.LocalDateTime;
import java.util.List;

public interface ChildProfileOutboxRepository {

    ChildProfileOutboxMessage save(ChildProfileOutboxMessage message);

    /**
     * 릴레이 잠금을 기다리지 않고 얻는다. 얻으면 true, 다른 트랜잭션이 쥐고 있으면 바로 false.
     * 잠금은 지금 트랜잭션이 끝날 때 풀리므로 트랜잭션 안에서 부르고, 보내기 · 기록을 같은 트랜잭션에서 한다
     * (서버가 여러 대여도 한 번에 하나의 릴레이만 돌아 같은 자녀의 이벤트 순서가 지켜진다).
     */
    boolean tryLockRelay();

    /**
     * 아직 보내지 않은 메시지를 id 오름차순으로 최대 limit건 찾는다.
     */
    List<ChildProfileOutboxMessage> findUnsent(int limit);

    /**
     * cutoff보다 먼저 보낸 메시지를 지운다. 아직 보내지 않은 메시지는 지우지 않는다.
     *
     * @return 지운 건수
     */
    int deleteSentBefore(LocalDateTime cutoff);
}
