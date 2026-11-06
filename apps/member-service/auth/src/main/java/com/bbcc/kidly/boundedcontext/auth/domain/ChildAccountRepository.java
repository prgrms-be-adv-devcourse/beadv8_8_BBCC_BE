package com.bbcc.kidly.boundedcontext.auth.domain;

import java.util.List;
import java.util.Optional;

public interface ChildAccountRepository {
    ChildAccount saveAccount(ChildAccount account);
    boolean existsByLoginId(String loginId);
    boolean existsByChildProfileId(Long childProfileId);
    Optional<ChildAccount> findByChildProfileIdForUpdate(Long childProfileId);
    Optional<ChildAccount> findByIdForUpdate(Long id);
    Optional<ChildAccount> findByIdAndPasswordHashForUpdate(Long id, String passwordHash);
    List<ChildAccount> findAllByGuardianIdOrderByIdAsc(Long guardianId);
    Optional<ChildAccount> findByLoginId(String loginId);
    void delete(ChildAccount account);
}
