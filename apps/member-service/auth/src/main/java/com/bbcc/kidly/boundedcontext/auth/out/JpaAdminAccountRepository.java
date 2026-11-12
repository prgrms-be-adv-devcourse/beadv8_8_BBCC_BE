package com.bbcc.kidly.boundedcontext.auth.out;

import com.bbcc.kidly.boundedcontext.auth.domain.AdminAccount;
import com.bbcc.kidly.boundedcontext.auth.domain.AdminAccountRepository;
import com.bbcc.kidly.boundedcontext.auth.domain.AdminStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface JpaAdminAccountRepository extends JpaRepository<AdminAccount, Long>, AdminAccountRepository {

    @Override
    default Page<AdminAccount> findAccounts(AdminStatus status, Pageable pageable) {
        var ordered = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
            Sort.by(Sort.Direction.DESC, "id"));
        return status == null ? findAll(ordered) : findByStatus(status, ordered);
    }

    Page<AdminAccount> findByStatus(AdminStatus status, Pageable pageable);

    @Override
    default AdminAccount saveAccount(AdminAccount account) {
        return saveAndFlush(account);
    }

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AdminAccount a where a.id = :id")
    Optional<AdminAccount> findByIdForUpdate(@Param("id") Long id);

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AdminAccount a where a.email = :email")
    Optional<AdminAccount> findByEmailForUpdate(@Param("email") String email);

    // 네이티브 쿼리라 JPA Auditing이 동작하지 않으므로 created_at · updated_at을 직접 넣는다.
    // 대상 없는 ON CONFLICT DO NOTHING이라 이메일 유니크와 부트스트랩 부분 유니크 인덱스 충돌을 모두 건너뛴다
    @Override
    @Transactional
    @Modifying
    @Query(value = """
        INSERT INTO auth.admin_account (email, password_hash, status, created_by, created_at, updated_at)
        SELECT :email, :passwordHash, 'ACTIVE', NULL, :now, :now
        WHERE NOT EXISTS (SELECT 1 FROM auth.admin_account)
        ON CONFLICT DO NOTHING
        """, nativeQuery = true)
    int insertBootstrapIfNone(@Param("email") String email, @Param("passwordHash") String passwordHash,
        @Param("now") LocalDateTime now);
}
