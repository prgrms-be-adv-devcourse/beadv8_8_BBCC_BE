package com.bbcc.kidly.shared.auth.out;

import java.time.LocalDateTime;

/**
 * 회원이 {@code revokedAt}까지 받은 Access Token을 무효화한다.
 * 호출자 트랜잭션 안에서만 부른다. 호출자가 커밋할 때만 효력이 생기고 롤백되면 아무 일도 없다.
 * 원격 구현도 이 뜻을 지켜야 한다(커밋 뒤 전송 또는 호출자 쪽 outbox).
 */
public interface MemberTokenRevoker {

    void revoke(Long memberId, LocalDateTime revokedAt);
}
