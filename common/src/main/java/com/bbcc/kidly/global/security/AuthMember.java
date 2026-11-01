package com.bbcc.kidly.global.security;

/**
 * 로그인 회원 정보. 컨트롤러에서 {@code @LoginMember AuthMember member}로 받는다({@link LoginMemberArgumentResolver}).
 * 역할은 담지 않는다. 관리자 · 판매자 API의 역할은 Gateway가 검사한다.
 * 로그로 샐 수 있어 {@link #toString()}에 닉네임을 넣지 않는다.
 *
 * @param nickname 토큰을 발급할 때의 닉네임. nickname 클레임이 없는 토큰(배포 전 발급)이면 null
 */
public record AuthMember(Long id, String nickname) {

    @Override
    public String toString() {
        return "AuthMember[id=" + id + "]";
    }
}
