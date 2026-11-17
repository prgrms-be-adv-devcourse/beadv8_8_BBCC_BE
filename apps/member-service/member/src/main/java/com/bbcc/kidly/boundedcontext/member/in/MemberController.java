package com.bbcc.kidly.boundedcontext.member.in;

import com.bbcc.kidly.boundedcontext.member.app.MemberService;
import com.bbcc.kidly.boundedcontext.member.app.dto.MemberUpdateRequest;
import com.bbcc.kidly.boundedcontext.member.in.dto.MemberResponse;
import com.bbcc.kidly.member.support.security.AuthMember;
import com.bbcc.kidly.member.support.security.LoginMember;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/members/me")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    @GetMapping
    public MemberResponse getMine(@LoginMember AuthMember member) {
        return MemberResponse.from(memberService.getMine(member.id()));
    }

    @PatchMapping
    public MemberResponse update(@LoginMember AuthMember member, @Valid @RequestBody MemberUpdateRequest request) {
        return MemberResponse.from(memberService.update(member.id(), request));
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void withdraw(@LoginMember AuthMember member) {
        memberService.withdraw(member.id());
    }
}
