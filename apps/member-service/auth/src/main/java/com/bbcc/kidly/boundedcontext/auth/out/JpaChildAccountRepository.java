package com.bbcc.kidly.boundedcontext.auth.out;

import com.bbcc.kidly.boundedcontext.auth.domain.ChildAccount;
import com.bbcc.kidly.boundedcontext.auth.domain.ChildAccountRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaChildAccountRepository extends JpaRepository<ChildAccount, Long>, ChildAccountRepository {
    @Override
    default ChildAccount saveAccount(ChildAccount account) {
        return save(account);
    }

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from ChildAccount a where a.id = :id")
    Optional<ChildAccount> findByIdForUpdate(@Param("id") Long id);
}
