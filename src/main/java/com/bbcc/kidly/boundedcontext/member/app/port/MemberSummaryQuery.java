package com.bbcc.kidly.boundedcontext.member.app.port;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * 관리자 회원 목록 조회. 탈퇴 회원은 빠지고, 관리자는 ADMIN 필터에서만 나온다.
 * keyword는 회원 번호 · 닉네임 · 이메일 검색, 비어 있으면 전체.
 */
public interface MemberSummaryQuery {

    Page<AdminMemberSummary> findAll(AdminMemberFilter filter, String keyword, Pageable pageable);
}
