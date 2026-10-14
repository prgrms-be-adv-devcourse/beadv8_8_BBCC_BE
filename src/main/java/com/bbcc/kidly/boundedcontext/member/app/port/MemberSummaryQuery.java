package com.bbcc.kidly.boundedcontext.member.app.port;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * 관리자 회원 목록 조회. 관리자 · 탈퇴 회원은 빠진다.
 */
public interface MemberSummaryQuery {

    Page<AdminMemberSummary> findAll(AdminMemberFilter filter, Pageable pageable);
}
