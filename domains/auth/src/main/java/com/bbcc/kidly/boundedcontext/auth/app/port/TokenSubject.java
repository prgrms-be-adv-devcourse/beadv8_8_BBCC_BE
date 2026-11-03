package com.bbcc.kidly.boundedcontext.auth.app.port;

import com.bbcc.kidly.shared.auth.PrincipalId;
import com.bbcc.kidly.shared.auth.Role;
import com.bbcc.kidly.shared.member.dto.MemberLoginState;
import java.util.List;

/**
 * Access Token을 발급할 주체. 종류마다 토큰에 담는 값이 다르다.
 */
public sealed interface TokenSubject {

    PrincipalId principal();

    // 닉네임은 화면 표시 · 신청 스냅샷용, 역할은 Gateway의 경로 권한용이다
    record Member(Long id, String nickname, List<Role> roles) implements TokenSubject {

        public static Member of(MemberLoginState.Active member) {
            return new Member(member.memberId(), member.nickname(), member.roles());
        }

        @Override
        public PrincipalId principal() {
            return PrincipalId.member(id);
        }
    }
}
