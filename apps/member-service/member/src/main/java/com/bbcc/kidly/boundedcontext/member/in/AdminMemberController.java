package com.bbcc.kidly.boundedcontext.member.in;

import com.bbcc.kidly.boundedcontext.member.app.AdminMemberService;
import com.bbcc.kidly.boundedcontext.member.app.MemberRegisteredRepublishService;
import com.bbcc.kidly.boundedcontext.member.app.dto.MemberSuspendRequest;
import com.bbcc.kidly.boundedcontext.member.app.port.AdminMemberFilter;
import com.bbcc.kidly.boundedcontext.member.in.dto.AdminMemberSummaryResponse;
import com.bbcc.kidly.boundedcontext.member.in.dto.MemberRegisteredRepublishResponse;
import com.bbcc.kidly.member.support.response.PageResponse;
import com.bbcc.kidly.member.support.security.AuthAdmin;
import com.bbcc.kidly.member.support.security.LoginAdmin;
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

@RestController
@RequestMapping("/api/v1/admin/members")
@RequiredArgsConstructor
public class AdminMemberController {

    private final AdminMemberService adminMemberService;
    private final MemberRegisteredRepublishService memberRegisteredRepublishService;

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
    public void suspend(@LoginAdmin AuthAdmin admin, @PathVariable Long memberId,
        @Valid @RequestBody MemberSuspendRequest request) {
        adminMemberService.suspend(memberId, admin.id(), request);
    }

    @PostMapping("/registered-events")
    public MemberRegisteredRepublishResponse republishRegisteredEvents() {
        return new MemberRegisteredRepublishResponse(memberRegisteredRepublishService.republishAll());
    }

}
