package com.bbcc.kidly.boundedcontext.member.domain;

/**
 * {@link Member#register} 입력. 값만 담는다.
 */
public record MemberRegisterInfo(String registrationKey, Nickname nickname, String email) {
}
