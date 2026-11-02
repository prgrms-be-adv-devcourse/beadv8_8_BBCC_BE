package com.bbcc.kidly.shared.member.out;

import com.bbcc.kidly.shared.member.dto.MemberLoginState;
import com.bbcc.kidly.shared.member.dto.MemberRegistration;
import java.util.Optional;

public interface MemberAccounts {

    /**
     * 가입 키로 회원을 만든다. 같은 키로 만든 회원이 이미 있으면 새로 만들지 않고 그 회원의 상태를 돌려준다.
     * 여러 번 · 동시에 불러도 회원 1명, member.registered 1건이다.
     * 트랜잭션 밖에서 부른다. 안에서 부르면 유니크 충돌 뒤 다시 찾지 못한다.
     * 닉네임이 다른 회원과 겹쳐 실패하면 DataIntegrityViolationException(409)을 던진다.
     */
    MemberLoginState register(MemberRegistration registration);

    // 회원 행은 지우지 않으므로 정상 흐름에서는 항상 있다
    Optional<MemberLoginState> findLoginState(Long memberId);
}
