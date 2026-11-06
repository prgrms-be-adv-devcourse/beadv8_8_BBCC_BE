package com.bbcc.kidly.boundedcontext.member.domain;

import com.bbcc.kidly.member.support.error.BusinessException;
import com.bbcc.kidly.member.support.error.CommonErrorCode;
import java.util.List;
import java.util.Optional;

public interface MemberRepository {

    Member save(Member member);

    Optional<Member> findById(Long id);

    default Member getById(Long id) {
        return findById(id).orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }

    Optional<Member> findByRegistrationKey(String registrationKey);

    boolean existsByNickname(String nickname);

    // 자신을 빼고 다른 회원이 쓰는 닉네임인지
    boolean existsByNicknameAndIdNot(String nickname, Long id);

    // 이메일이 있는 회원. 탈퇴하면 이메일을 지우므로 탈퇴 회원은 빠진다
    List<Member> findByEmailIsNotNull();

    // 초기 데이터를 넣을지 판단한다
    long count();
}
