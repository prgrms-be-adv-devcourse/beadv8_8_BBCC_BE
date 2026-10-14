package com.bbcc.kidly.boundedcontext.member;

import com.bbcc.kidly.boundedcontext.member.domain.Member;
import com.bbcc.kidly.boundedcontext.member.domain.MemberRole;
import com.bbcc.kidly.boundedcontext.member.domain.MemberStatus;
import com.bbcc.kidly.boundedcontext.member.domain.OAuthProvider;
import com.bbcc.kidly.shared.seller.domain.SellerStatus;
import java.time.LocalDateTime;
import org.springframework.test.util.ReflectionTestUtils;

public final class MemberFixture {

    public static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 12, 9, 0);

    public static final String NICKNAME = "테스트회원";

    private MemberFixture() {
    }

    public static Member kakaoMember() {
        return Member.register(OAuthProvider.KAKAO, "kakao-1", NICKNAME, NOW);
    }

    // 닉네임은 회원끼리 겹칠 수 없어서 소셜 사용자 ID로 만든다
    public static Member kakaoMember(String providerUserId) {
        return Member.register(OAuthProvider.KAKAO, providerUserId, "회원-" + providerUserId, NOW);
    }

    // ADMIN은 시드 데이터로만 부여하고 탈퇴 메서드는 아직 없어서 테스트에서는 필드를 직접 바꾼다
    public static Member withRole(MemberRole role) {
        return withRole(kakaoMember(), role);
    }

    public static Member withRole(String providerUserId, MemberRole role) {
        return withRole(kakaoMember(providerUserId), role);
    }

    public static Member withStatus(MemberStatus status) {
        return withStatus(kakaoMember(), status);
    }

    public static Member withStatus(String providerUserId, MemberStatus status) {
        return withStatus(kakaoMember(providerUserId), status);
    }

    public static Member withSellerState(SellerStatus status) {
        return withSellerState(kakaoMember(), status);
    }

    public static Member withSellerState(String providerUserId, SellerStatus status) {
        return withSellerState(kakaoMember(providerUserId), status);
    }

    private static Member withRole(Member member, MemberRole role) {
        ReflectionTestUtils.setField(member, "role", role);
        return member;
    }

    private static Member withStatus(Member member, MemberStatus status) {
        ReflectionTestUtils.setField(member, "status", status);
        return member;
    }

    private static Member withSellerState(Member member, SellerStatus status) {
        member.applySellerState(status, NOW);
        return member;
    }
}
