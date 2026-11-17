package com.bbcc.kidly.boundedcontext.member.app.dto;

import com.bbcc.kidly.boundedcontext.member.domain.Member;
import com.bbcc.kidly.shared.auth.Role;
import com.bbcc.kidly.shared.seller.domain.SellerStatus;
import java.util.List;

public record MemberResult(Long id, String nickname, String email, List<Role> roles, SellerStatus sellerStatus,
    List<String> providers) {

    public static MemberResult from(Member member, List<String> providers) {
        return new MemberResult(member.getId(), member.getNickname(), member.getEmail(), member.roles(),
            member.sellerStatus(), providers);
    }
}
