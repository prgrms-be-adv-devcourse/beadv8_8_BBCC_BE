package com.bbcc.kidly.boundedcontext.member;

import com.bbcc.kidly.boundedcontext.member.domain.Member;
import com.bbcc.kidly.boundedcontext.member.domain.MemberRole;
import com.bbcc.kidly.boundedcontext.member.domain.MemberStatus;
import com.bbcc.kidly.boundedcontext.member.domain.OAuthProvider;
import java.time.LocalDateTime;
import org.springframework.test.util.ReflectionTestUtils;

public final class MemberFixture {

    public static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 12, 9, 0);

    private MemberFixture() {
    }

    public static Member kakaoMember() {
        return kakaoMember("kakao-1");
    }

    public static Member kakaoMember(String providerUserId) {
        return Member.register(OAuthProvider.KAKAO, providerUserId, "테스트회원", NOW);
    }

    // ADMIN은 시드 데이터로만 부여하고 정지 · 탈퇴 메서드는 아직 없어서 테스트에서는 필드를 직접 바꾼다
    public static Member withRole(MemberRole role) {
        Member member = kakaoMember();
        ReflectionTestUtils.setField(member, "role", role);
        return member;
    }

    public static Member withStatus(MemberStatus status) {
        Member member = kakaoMember();
        ReflectionTestUtils.setField(member, "status", status);
        return member;
    }
}
