package com.bbcc.kidly.boundedcontext.member;

import com.bbcc.kidly.boundedcontext.member.domain.Member;
import com.bbcc.kidly.boundedcontext.member.domain.MemberRegisterInfo;
import com.bbcc.kidly.boundedcontext.member.domain.MemberStatus;
import com.bbcc.kidly.boundedcontext.member.domain.Nickname;
import com.bbcc.kidly.shared.seller.domain.SellerStatus;
import java.time.LocalDateTime;
import org.springframework.test.util.ReflectionTestUtils;

public final class MemberFixture {

    public static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 12, 9, 0);

    public static final String NICKNAME = "테스트회원";

    public static final String EMAIL = "kakao-1@example.com";

    private MemberFixture() {
    }

    public static Member kakaoMember() {
        return register("kakao-1", NICKNAME);
    }

    public static Member kakaoMember(String providerUserId) {
        return register(providerUserId, "회원-" + providerUserId);
    }

    public static Member withNickname(String providerUserId, String nickname) {
        return register(providerUserId, nickname);
    }

    public static Member withoutEmail(String providerUserId) {
        var member = kakaoMember(providerUserId);
        ReflectionTestUtils.setField(member, "email", null);
        return member;
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
        return Member.register(new MemberRegisterInfo(null, new Nickname(nickname), email));
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
