package com.bbcc.kidly.boundedcontext.auth.domain;

import java.time.LocalDateTime;
import java.util.Optional;

public interface AdminAccountRepository {

    AdminAccount saveAccount(AdminAccount account);

    Optional<AdminAccount> findByIdForUpdate(Long id);

    Optional<AdminAccount> findByEmailForUpdate(String email);

    // 관리자가 한 명도 없을 때만 첫 관리자를 넣고 넣은 행 수(0 또는 1)를 돌려준다
    // 두 번 돌아도, 두 인스턴스가 서로 다른 이메일로 동시에 떠도 한 행이다(uk_admin_account_bootstrap)
    int insertBootstrapIfNone(String email, String passwordHash, LocalDateTime now);
}
