package com.bbcc.kidly.boundedcontext.member.in.dto;

import com.bbcc.kidly.boundedcontext.member.app.dto.MemberResult;
import com.bbcc.kidly.global.security.Role;
import java.util.List;

/**
 * @param nickname 화면은 이 값을 회원 이름으로 보여 준다
 */
public record MemberResponse(Long id, String nickname, List<Role> roles) {

    public static MemberResponse from(MemberResult result) {
        return new MemberResponse(result.id(), result.nickname(), result.roles());
    }
}
