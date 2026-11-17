package com.bbcc.kidly.boundedcontext.auth.domain;

import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AdminAccountRepository {

    Page<AdminAccount> findAccounts(AdminStatus status, Pageable pageable);

    boolean existsByEmail(String email);

    AdminAccount saveAccount(AdminAccount account);

    Optional<AdminAccount> findByIdForUpdate(Long id);

    Optional<AdminAccount> findByEmailForUpdate(String email);

    int insertBootstrapIfNone(String email, String passwordHash, LocalDateTime now);
}
