package com.bbcc.kidly.boundedcontext.notification.domain;

import java.util.Optional;

public interface MemberContactRepository {

    MemberContact save(MemberContact contact);

    Optional<MemberContact> findById(Long memberId);

    boolean existsById(Long memberId);

    // 없는 회원이면 아무것도 하지 않는다
    void deleteById(Long memberId);
}
