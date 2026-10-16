package com.bbcc.kidly.boundedcontext.member.in;

import com.bbcc.kidly.boundedcontext.member.app.AdminMemberService;
import com.bbcc.kidly.boundedcontext.member.app.dto.MemberSuspendRequest;
import com.bbcc.kidly.boundedcontext.member.app.port.AdminMemberFilter;
import com.bbcc.kidly.boundedcontext.member.in.dto.AdminMemberSummaryResponse;
import com.bbcc.kidly.global.response.PageResponse;
import com.bbcc.kidly.global.security.AuthMember;
import com.bbcc.kidly.global.security.LoginMember;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 회원 API. /api/v1/admin/** 라 ADMIN만 호출할 수 있다.
 */
@RestController
@RequestMapping("/api/v1/admin/members")
@RequiredArgsConstructor
public class AdminMemberController {

    private final AdminMemberService adminMemberService;

    // filter가 없으면 전체 일반 회원, ADMIN이면 관리자만. keyword는 회원 번호 · 닉네임 · 이메일 검색. 탈퇴 회원은 빠진다
    @GetMapping
    public PageResponse<AdminMemberSummaryResponse> list(
        @RequestParam(defaultValue = "ALL") AdminMemberFilter filter,
        @RequestParam(required = false) String keyword,
        @PageableDefault Pageable pageable) {
        return PageResponse.from(
            adminMemberService.getMembers(filter, keyword, pageable).map(AdminMemberSummaryResponse::from));
    }

    @PostMapping("/{memberId}/suspend")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void suspend(@LoginMember AuthMember admin, @PathVariable Long memberId,
        @Valid @RequestBody MemberSuspendRequest request) {
        adminMemberService.suspend(memberId, admin.id(), request);
    }

    @PostMapping("/{memberId}/grant-admin")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void grantAdmin(@PathVariable Long memberId) {
        adminMemberService.grantAdmin(memberId);
    }

    @PostMapping("/{memberId}/revoke-admin")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeAdmin(@LoginMember AuthMember admin, @PathVariable Long memberId) {
        adminMemberService.revokeAdmin(memberId, admin.id());
    }
}
