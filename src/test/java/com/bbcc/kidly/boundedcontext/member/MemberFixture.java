package com.bbcc.kidly.boundedcontext.member;

import com.bbcc.kidly.boundedcontext.member.domain.Member;
import com.bbcc.kidly.boundedcontext.member.domain.MemberRegisterInfo;
import com.bbcc.kidly.boundedcontext.member.domain.MemberRole;
import com.bbcc.kidly.boundedcontext.member.domain.MemberStatus;
import com.bbcc.kidly.boundedcontext.member.domain.Nickname;
import com.bbcc.kidly.boundedcontext.member.domain.OAuthProvider;
import com.bbcc.kidly.boundedcontext.member.domain.SocialIdentity;
import com.bbcc.kidly.shared.seller.domain.SellerStatus;
import java.time.LocalDateTime;
import org.springframework.test.util.ReflectionTestUtils;

public final class MemberFixture {

    public static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 12, 9, 0);

    public static final String NICKNAME = "테스트회원";

    // 기본 kakaoMember()의 이메일이다
    public static final String EMAIL = "kakao-1@example.com";

    private MemberFixture() {
    }

    public static Member kakaoMember() {
        return register("kakao-1", NICKNAME);
    }

    // 닉네임은 회원끼리 겹칠 수 없어서 소셜 사용자 ID로 만든다
    public static Member kakaoMember(String providerUserId) {
        return register(providerUserId, "회원-" + providerUserId);
    }

    // 닉네임을 직접 정해야 하는 검색 테스트용이다
    public static Member withNickname(String providerUserId, String nickname) {
        return register(providerUserId, nickname);
    }

    // 이메일을 받기 전에 가입한 회원은 이메일이 없다
    public static Member withoutEmail(String providerUserId) {
        var member = kakaoMember(providerUserId);
        ReflectionTestUtils.setField(member, "email", null);
        return member;
    }

    // 관리자 지정 규칙(ACTIVE만 가능)을 거치지 않고, 탈퇴 메서드는 아직 없어서 테스트에서는 필드를 직접 바꾼다
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

    private static Member register(String providerUserId, String nickname) {
        var email = providerUserId + "@example.com";
        var identity = new SocialIdentity(OAuthProvider.KAKAO, providerUserId);
        return Member.register(new MemberRegisterInfo(identity, new Nickname(nickname), email), NOW);
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
