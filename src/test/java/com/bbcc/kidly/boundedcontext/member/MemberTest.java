package com.bbcc.kidly.boundedcontext.member;

import static com.bbcc.kidly.boundedcontext.member.MemberFixture.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.member.domain.Member;
import com.bbcc.kidly.boundedcontext.member.domain.MemberRole;
import com.bbcc.kidly.boundedcontext.member.domain.MemberStatus;
import com.bbcc.kidly.boundedcontext.member.domain.OAuthProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class MemberTest {

    @Test
    @DisplayName("가입하면 소셜 계정 1개를 가진 활성 일반 회원이 된다")
    void register() {
        Member member = Member.register(OAuthProvider.KAKAO, "kakao-1", "테스트회원", NOW);

        assertThat(member.getNickname()).isEqualTo("테스트회원");
        assertThat(member.getRole()).isEqualTo(MemberRole.USER);
        assertThat(member.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(member.getSocialAccounts()).singleElement().satisfies(account -> {
            assertThat(account.getProvider()).isEqualTo(OAuthProvider.KAKAO);
            assertThat(account.getProviderUserId()).isEqualTo("kakao-1");
            assertThat(account.getConnectedAt()).isEqualTo(NOW);
            assertThat(account.getMember()).isSameAs(member);
        });
    }

    @Test
    @DisplayName("가입에 필요한 값이 하나라도 null이면 가입할 수 없다")
    void registerRejectsNull() {
        assertThatThrownBy(() -> Member.register(null, "kakao-1", "테스트회원", NOW))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Member.register(OAuthProvider.KAKAO, null, "테스트회원", NOW))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Member.register(OAuthProvider.KAKAO, "kakao-1", null, NOW))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Member.register(OAuthProvider.KAKAO, "kakao-1", "테스트회원", null))
            .isInstanceOf(NullPointerException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " "})
    @DisplayName("닉네임이나 소셜 사용자 ID가 비어 있으면 가입할 수 없다")
    void registerRejectsBlank(String blank) {
        assertThatThrownBy(() -> Member.register(OAuthProvider.KAKAO, blank, "테스트회원", NOW))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Member.register(OAuthProvider.KAKAO, "kakao-1", blank, NOW))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("활성 회원은 로그인할 수 있다")
    void activeMemberCanLogin() {
        assertThat(MemberFixture.kakaoMember().canLogin()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = MemberStatus.class, names = {"SUSPENDED", "WITHDRAWN"})
    @DisplayName("정지 · 탈퇴한 회원은 로그인할 수 없다")
    void inactiveMemberCannotLogin(MemberStatus status) {
        assertThat(MemberFixture.withStatus(status).canLogin()).isFalse();
    }

    @Test
    @DisplayName("역할이 ADMIN이면 관리자다")
    void admin() {
        assertThat(MemberFixture.withRole(MemberRole.ADMIN).isAdmin()).isTrue();
        assertThat(MemberFixture.kakaoMember().isAdmin()).isFalse();
    }

    @Test
    @DisplayName("소셜 계정 목록은 밖에서 바꿀 수 없다")
    void socialAccountsAreReadOnly() {
        Member member = MemberFixture.kakaoMember();

        assertThatThrownBy(() -> member.getSocialAccounts().clear())
            .isInstanceOf(UnsupportedOperationException.class);
    }
}
