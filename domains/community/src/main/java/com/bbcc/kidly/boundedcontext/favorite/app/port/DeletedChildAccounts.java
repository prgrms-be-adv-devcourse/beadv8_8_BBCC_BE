package com.bbcc.kidly.boundedcontext.favorite.app.port;

import java.time.LocalDateTime;

/**
 * 지운 자녀 계정 표와 자녀 계정별 잠금(트랜잭션 advisory lock, 부르는 쪽 트랜잭션 안에서만). 구현은 out/persistence.
 */
public interface DeletedChildAccounts {

    boolean existsById(Long accountId);

    /**
     * 그 자녀 계정의 삭제 처리를 시작한다: 트랜잭션이 끝날 때까지 그 계정의 찜 추가 저장을 기다리게 한다(배타 잠금).
     */
    void lockChildAccountExclusive(Long accountId);

    /**
     * 그 자녀 계정의 찜 추가 저장을 시작한다: 겹친 삭제 처리가 끝날 때까지 기다린다(공유 잠금, 같은 계정의 찜 추가끼리는 막지 않는다).
     */
    void lockChildAccountShared(Long accountId);

    /**
     * 지운 자녀 계정으로 남긴다. 이미 있으면 그대로 둔다(INSERT … ON CONFLICT DO NOTHING).
     *
     * @return 새로 남겼으면 1, 이미 있었으면 0
     */
    int markDeleted(Long accountId, Long guardianId, LocalDateTime deletedAt, LocalDateTime now);
}
