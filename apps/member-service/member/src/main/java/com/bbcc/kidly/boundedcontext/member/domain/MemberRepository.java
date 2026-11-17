package com.bbcc.kidly.boundedcontext.member.domain;

import com.bbcc.kidly.member.support.error.BusinessException;
import com.bbcc.kidly.member.support.error.CommonErrorCode;
import java.util.List;
import java.util.Optional;

public interface MemberRepository {

    Member save(Member member);

    Optional<Member> findById(Long id);

    Optional<Member> findByIdForUpdate(Long id);

    default Member getById(Long id) {
        return findById(id).orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }

    Optional<Member> findByRegistrationKey(String registrationKey);

    boolean existsByNickname(String nickname);

    boolean existsByNicknameAndIdNot(String nickname, Long id);

    List<Member> findByEmailIsNotNull();

    long count();
}
