package com.bbcc.kidly.boundedcontext.member.domain;

/**
 * {@link Member#register} 입력. 값만 담고, 검증은 엔티티가 한다.
 */
public record MemberRegisterInfo(OAuthProvider provider, String providerUserId, String nickname, String email) {
}
