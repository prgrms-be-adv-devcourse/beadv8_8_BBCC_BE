package com.bbcc.kidly.boundedcontext.auth.out;

import com.bbcc.kidly.boundedcontext.auth.domain.ChildAccount;
import com.bbcc.kidly.boundedcontext.auth.domain.ChildAccountRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaChildAccountRepository extends JpaRepository<ChildAccount, Long>, ChildAccountRepository {
    @Override
    default ChildAccount saveAccount(ChildAccount account) {
        return save(account);
    }
}
