package com.bbcc.kidly.boundedcontext.member.domain;

import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.global.error.CommonErrorCode;
import java.util.Optional;

public interface MemberRepository {

    Member save(Member member);

    Optional<Member> findById(Long id);

    default Member getById(Long id) {
        return findById(id).orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }

    Optional<Member> findBySocialAccount(OAuthProvider provider, String providerUserId);
}
