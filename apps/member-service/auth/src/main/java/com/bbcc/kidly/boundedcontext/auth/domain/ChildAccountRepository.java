package com.bbcc.kidly.boundedcontext.auth.domain;

import java.util.Optional;

public interface ChildAccountRepository {
    ChildAccount saveAccount(ChildAccount account);
    boolean existsByLoginId(String loginId);
    boolean existsByChildProfileId(Long childProfileId);
    Optional<ChildAccount> findByChildProfileId(Long childProfileId);
    Optional<ChildAccount> findByIdForUpdate(Long id);
    Optional<ChildAccount> findByIdAndPasswordHashForUpdate(Long id, String passwordHash);
    Optional<ChildAccount> findByLoginId(String loginId);
    void delete(ChildAccount account);
}
