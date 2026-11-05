package com.bbcc.kidly.boundedcontext.auth.app.port;

import com.bbcc.kidly.shared.auth.PrincipalId;
import com.bbcc.kidly.shared.auth.Role;
import com.bbcc.kidly.shared.member.dto.MemberLoginState;
import java.util.List;

public sealed interface TokenSubject {

    PrincipalId principal();

    record Member(Long id, String nickname, List<Role> roles) implements TokenSubject {

        public static Member of(MemberLoginState.Active member) {
            return new Member(member.memberId(), member.nickname(), member.roles());
        }

        @Override
        public PrincipalId principal() {
            return PrincipalId.member(id);
        }
    }

    record Admin(Long id) implements TokenSubject {

        @Override
        public PrincipalId principal() {
            return PrincipalId.admin(id);
        }
    }

    record Child(Long id, Long guardianId) implements TokenSubject {
        @Override
        public PrincipalId principal() {
            return PrincipalId.child(id);
        }
    }
}
