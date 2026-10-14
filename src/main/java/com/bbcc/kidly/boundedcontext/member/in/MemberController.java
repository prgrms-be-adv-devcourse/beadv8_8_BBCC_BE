package com.bbcc.kidly.boundedcontext.member.in;

import com.bbcc.kidly.boundedcontext.member.app.MemberService;
import com.bbcc.kidly.boundedcontext.member.app.dto.MemberUpdateRequest;
import com.bbcc.kidly.boundedcontext.member.in.dto.MemberResponse;
import com.bbcc.kidly.global.security.AuthMember;
import com.bbcc.kidly.global.security.LoginMember;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 로그인한 회원 본인의 정보 API.
 */
@RestController
@RequestMapping("/api/v1/members/me")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    @GetMapping
    public MemberResponse getMine(@LoginMember AuthMember member) {
        return MemberResponse.from(memberService.getMine(member.id()));
    }

    // 닉네임을 바꾼 뒤 프론트는 POST /auth/reissue로 토큰을 다시 받는다. 판매자 신청은 토큰의 닉네임으로 저장된다
    @PatchMapping
    public MemberResponse update(@LoginMember AuthMember member, @Valid @RequestBody MemberUpdateRequest request) {
        return MemberResponse.from(memberService.update(member.id(), request));
    }
}
