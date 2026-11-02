package com.bbcc.kidly.boundedcontext.member.domain;

/**
 * {@link Member#register} 입력. 값만 담고, 검증은 엔티티가 한다.
 * 가입 키로 가입하면 identity가 null이고, 소셜 계정으로 바로 가입하면 registrationKey가 null이다.
 */
public record MemberRegisterInfo(String registrationKey, SocialIdentity identity, Nickname nickname, String email) {

    public MemberRegisterInfo(String registrationKey, Nickname nickname, String email) {
        this(registrationKey, null, nickname, email);
    }

    public MemberRegisterInfo(SocialIdentity identity, Nickname nickname, String email) {
        this(null, identity, nickname, email);
    }
}
