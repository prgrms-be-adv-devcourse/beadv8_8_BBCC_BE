package com.bbcc.kidly.boundedcontext.auth.app.port;

import com.bbcc.kidly.shared.auth.Role;
import com.bbcc.kidly.shared.member.dto.MemberLoginState;
import java.util.List;

public record TokenSubject(Long id, String nickname, List<Role> roles) {

    public static TokenSubject of(MemberLoginState.Active member) {
        return new TokenSubject(member.memberId(), member.nickname(), member.roles());
    }
}
