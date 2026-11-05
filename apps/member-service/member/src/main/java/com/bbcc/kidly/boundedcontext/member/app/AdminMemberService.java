package com.bbcc.kidly.boundedcontext.member.app;

import com.bbcc.kidly.boundedcontext.member.app.dto.MemberSuspendRequest;
import com.bbcc.kidly.boundedcontext.member.app.port.AdminMemberFilter;
import com.bbcc.kidly.boundedcontext.member.app.port.AdminMemberSummary;
import com.bbcc.kidly.boundedcontext.member.app.port.MemberSummaryQuery;
import com.bbcc.kidly.boundedcontext.member.domain.MemberRepository;
import com.bbcc.kidly.shared.auth.out.MemberTokenRevoker;
import com.bbcc.kidly.shared.member.event.MemberSuspendedEvent;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자의 회원 관리. 정지된 회원은 로그인 · 토큰 재발급이 막히고, 이미 발급된 Access Token도 바로 무효화된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminMemberService {

    private final MemberRepository memberRepository;
    private final MemberSummaryQuery memberSummaryQuery;
    private final MemberTokenRevoker memberTokenRevoker;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public Page<AdminMemberSummary> getMembers(AdminMemberFilter filter, String keyword, Pageable pageable) {
        return memberSummaryQuery.findAll(filter, keyword, pageable);
    }

    // 동시에 정지하면 둘 다 성공하고 나중 사유가 남는다(@Version 없음, 상태는 같다)
    @Transactional
    public void suspend(Long memberId, Long adminId, MemberSuspendRequest request) {
        var now = LocalDateTime.now();
        memberRepository.getById(memberId).suspend(adminId, request.reason(), now);
        memberTokenRevoker.revoke(memberId, now);
        eventPublisher.publishEvent(new MemberSuspendedEvent(UUID.randomUUID(), now,
            new MemberSuspendedEvent.Data(memberId, request.reason(), now)));
        log.info("회원 정지: memberId={} adminId={}", memberId, adminId);
    }

}
