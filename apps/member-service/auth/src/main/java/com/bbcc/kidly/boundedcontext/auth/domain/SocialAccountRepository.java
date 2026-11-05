package com.bbcc.kidly.boundedcontext.auth.domain;

import java.util.List;
import java.util.Optional;

public interface SocialAccountRepository {

    SocialAccount save(SocialAccount account);

    Optional<SocialAccount> findByIdentity(SocialIdentity identity);

    List<SocialAccount> findByMemberId(Long memberId);

    void link(Long id, Long memberId);

    void deleteById(Long id);

    void deleteByMemberId(Long memberId);
}
