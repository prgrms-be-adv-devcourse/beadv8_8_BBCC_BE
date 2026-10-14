package com.bbcc.kidly.boundedcontext.member.app;

import com.bbcc.kidly.boundedcontext.member.app.dto.MemberSuspendRequest;
import com.bbcc.kidly.boundedcontext.member.app.port.AdminMemberFilter;
import com.bbcc.kidly.boundedcontext.member.app.port.AdminMemberSummary;
import com.bbcc.kidly.boundedcontext.member.app.port.MemberSummaryQuery;
import com.bbcc.kidly.boundedcontext.member.domain.MemberRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자의 회원 관리. 정지된 회원은 로그인 · 토큰 재발급이 막히고, 이미 발급된 Access Token은 만료까지 유효하다.
 */
@Service
@RequiredArgsConstructor
public class AdminMemberService {

    private final MemberRepository memberRepository;
    private final MemberSummaryQuery memberSummaryQuery;

    @Transactional(readOnly = true)
    public Page<AdminMemberSummary> getMembers(AdminMemberFilter filter, String keyword, Pageable pageable) {
        return memberSummaryQuery.findAll(filter, keyword, pageable);
    }

    // 동시에 정지하면 둘 다 성공하고 나중 사유가 남는다(@Version 없음, 상태는 같다)
    @Transactional
    public void suspend(Long memberId, Long adminId, MemberSuspendRequest request) {
        memberRepository.getById(memberId).suspend(adminId, request.reason(), LocalDateTime.now());
    }
}
