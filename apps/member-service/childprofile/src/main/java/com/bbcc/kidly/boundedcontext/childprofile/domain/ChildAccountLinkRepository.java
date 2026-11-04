package com.bbcc.kidly.boundedcontext.childprofile.domain;

import java.util.Optional;

public interface ChildAccountLinkRepository {
    Optional<ChildAccountLink> findById(Long accountId);

    ChildAccountLink save(ChildAccountLink link);
}
