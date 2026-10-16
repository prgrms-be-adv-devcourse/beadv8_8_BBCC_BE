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

    boolean existsByNickname(String nickname);

    // 자신을 빼고 다른 회원이 쓰는 닉네임인지
    boolean existsByNicknameAndIdNot(String nickname, Long id);

    // 초기 데이터를 넣을지 판단한다
    long count();
}
