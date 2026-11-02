package com.bbcc.kidly.shared.member.dto;

import com.bbcc.kidly.shared.auth.Role;
import java.util.List;

/**
 * 회원에게 토큰을 줄 수 있는지와 토큰에 담을 값.
 */
public sealed interface MemberLoginState {

    Long memberId();

    record Active(Long memberId, String nickname, List<Role> roles) implements MemberLoginState {
    }

    record Suspended(Long memberId) implements MemberLoginState {
    }

    record Withdrawn(Long memberId) implements MemberLoginState {
    }
}
