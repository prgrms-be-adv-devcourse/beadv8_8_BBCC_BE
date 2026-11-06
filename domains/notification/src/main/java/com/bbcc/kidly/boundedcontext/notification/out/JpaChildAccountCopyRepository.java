package com.bbcc.kidly.boundedcontext.notification.out;

import com.bbcc.kidly.boundedcontext.notification.domain.ChildAccountCopy;
import com.bbcc.kidly.boundedcontext.notification.domain.ChildAccountCopyRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaChildAccountCopyRepository extends JpaRepository<ChildAccountCopy, Long>,
    ChildAccountCopyRepository {
}
