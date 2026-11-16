package com.bbcc.kidly.boundedcontext.favorite.out.persistence;

import com.bbcc.kidly.boundedcontext.favorite.app.port.DeletedChildAccounts;
import com.bbcc.kidly.boundedcontext.favorite.domain.DeletedChildAccount;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 지운 자녀 계정 저장소.
 */
public interface JpaDeletedChildAccountRepository extends JpaRepository<DeletedChildAccount, Long>,
    DeletedChildAccounts {

    @Override
    @Modifying
    @Query(nativeQuery = true, value = "insert into community.deleted_child_account (account_id, guardian_id, "
        + "deleted_at, created_at) values (:accountId, :guardianId, :deletedAt, :now) "
        + "on conflict (account_id) do nothing")
    int markDeleted(@Param("accountId") Long accountId, @Param("guardianId") Long guardianId,
        @Param("deletedAt") LocalDateTime deletedAt, @Param("now") LocalDateTime now);
}
