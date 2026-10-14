package com.bbcc.kidly.boundedcontext.member.app;

import com.bbcc.kidly.boundedcontext.member.app.dto.MemberResult;
import com.bbcc.kidly.boundedcontext.member.domain.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 본인의 정보를 조회한다. 역할은 토큰이 아니라 DB의 현재 값으로 계산하므로 판매자 승인 직후 재발급 전에도 SELLER가 보인다.
 */
@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;

    @Transactional(readOnly = true)
    public MemberResult getMine(Long memberId) {
        return MemberResult.from(memberRepository.getById(memberId));
    }
}
