package com.bbcc.kidly.shared.member.dto;

/**
 * {@link com.bbcc.kidly.shared.member.out.MemberAccounts#register} 입력.
 *
 * @param registrationKey 부르는 쪽이 정한 불투명한 멱등 키. 회원은 뜻을 해석하지 않는다
 * @param nicknameHint    닉네임을 만들 재료. 없을 수 있다
 * @param email           비어 있지 않다(부르는 쪽이 확인했다)
 */
public record MemberRegistration(String registrationKey, String nicknameHint, String email) {
}
