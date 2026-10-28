package com.bbcc.kidly.boundedcontext.member.in.dto;

import com.bbcc.kidly.boundedcontext.member.app.dto.MemberResult;
import com.bbcc.kidly.global.security.Role;
import com.bbcc.kidly.shared.seller.domain.SellerStatus;
import java.util.List;

/**
 * @param nickname 화면은 이 값을 회원 이름으로 보여 준다
 * @param email 가입 때 받은 카카오 이메일. 이메일을 받기 전에 가입한 회원은 null
 * @param sellerStatus 판매자가 아니면 null. 정지되면 SUSPENDED이고 roles에는 SELLER가 없다
 * @param providers 로그인에 쓰는 소셜 제공자 (예: KAKAO). 화면의 "로그인 계정"에 보여 준다
 */
public record MemberResponse(Long id, String nickname, String email, List<Role> roles, SellerStatus sellerStatus,
    List<String> providers) {

    public static MemberResponse from(MemberResult result) {
        return new MemberResponse(result.id(), result.nickname(), result.email(), result.roles(),
            result.sellerStatus(), result.providers());
    }
}
