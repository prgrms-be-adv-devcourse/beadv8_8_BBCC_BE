package com.bbcc.kidly.boundedcontext.member.app.dto;

import com.bbcc.kidly.boundedcontext.member.domain.Member;
import com.bbcc.kidly.global.security.Role;
import java.util.List;

/**
 * 내 정보 조회 결과. 컨트롤러는 엔티티 대신 이것으로 응답을 만든다.
 */
public record MemberResult(Long id, String nickname, List<Role> roles) {

    public static MemberResult from(Member member) {
        return new MemberResult(member.getId(), member.getNickname(), member.roles());
    }
}
