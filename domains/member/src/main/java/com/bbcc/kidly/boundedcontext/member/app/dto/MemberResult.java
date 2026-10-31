package com.bbcc.kidly.boundedcontext.member.app.dto;

import com.bbcc.kidly.boundedcontext.member.domain.Member;
import com.bbcc.kidly.shared.auth.Role;
import com.bbcc.kidly.shared.seller.domain.SellerStatus;
import java.util.List;

/**
 * 내 정보 조회 · 수정 결과. 컨트롤러는 엔티티 대신 이것으로 응답을 만든다.
 *
 * @param providers 로그인에 쓰는 소셜 제공자 이름 (예: KAKAO)
 */
public record MemberResult(Long id, String nickname, String email, List<Role> roles, SellerStatus sellerStatus,
    List<String> providers) {

    public static MemberResult from(Member member) {
        var providers = member.getSocialAccounts().stream()
            .map(account -> account.getIdentity().provider().name())
            .toList();
        return new MemberResult(member.getId(), member.getNickname(), member.getEmail(), member.roles(),
            member.sellerStatus(), providers);
    }
}
