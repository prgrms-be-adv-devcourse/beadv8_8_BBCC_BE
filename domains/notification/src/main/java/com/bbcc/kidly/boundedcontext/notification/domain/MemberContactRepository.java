package com.bbcc.kidly.boundedcontext.notification.domain;

import java.util.Optional;

public interface MemberContactRepository {

    MemberContact save(MemberContact contact);

    Optional<MemberContact> findById(Long memberId);

    boolean existsById(Long memberId);

    void deleteById(Long memberId);
}
