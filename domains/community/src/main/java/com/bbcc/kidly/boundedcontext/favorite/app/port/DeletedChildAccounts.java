package com.bbcc.kidly.boundedcontext.favorite.app.port;

import java.time.LocalDateTime;

/**
 * 지운 자녀 계정 표. 구현은 out/persistence.
 */
public interface DeletedChildAccounts {

    boolean existsById(Long accountId);

    /**
     * 지운 자녀 계정으로 남긴다. 이미 있으면 그대로 둔다(INSERT … ON CONFLICT DO NOTHING).
     *
     * @return 새로 남겼으면 1, 이미 있었으면 0
     */
    int markDeleted(Long accountId, Long guardianId, LocalDateTime deletedAt, LocalDateTime now);
}
