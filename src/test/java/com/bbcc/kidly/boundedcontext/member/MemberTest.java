package com.bbcc.kidly.boundedcontext.member;

import static com.bbcc.kidly.boundedcontext.member.MemberFixture.EMAIL;
import static com.bbcc.kidly.boundedcontext.member.MemberFixture.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.member.domain.Member;
import com.bbcc.kidly.boundedcontext.member.domain.MemberErrorCode;
import com.bbcc.kidly.boundedcontext.member.domain.MemberRegisterInfo;
import com.bbcc.kidly.boundedcontext.member.domain.MemberRole;
import com.bbcc.kidly.boundedcontext.member.domain.MemberStatus;
import com.bbcc.kidly.boundedcontext.member.domain.MemberSuspension;
import com.bbcc.kidly.boundedcontext.member.domain.OAuthProvider;
import com.bbcc.kidly.boundedcontext.member.domain.SocialIdentity;
import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.global.security.Role;
import com.bbcc.kidly.shared.seller.domain.SellerStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

class MemberTest {

    private static final Long ADMIN_ID = 99L;
    private static final String REASON = "약관 위반";
    private static final SocialIdentity KAKAO_1 = new SocialIdentity(OAuthProvider.KAKAO, "kakao-1");

    @Test
    @DisplayName("가입하면 소셜 계정 1개를 가진 활성 일반 회원이 된다")
    void register() {
        Member member = Member.register(new MemberRegisterInfo(KAKAO_1, "테스트회원", EMAIL), NOW);

        assertThat(member.getNickname()).isEqualTo("테스트회원");
        assertThat(member.getEmail()).isEqualTo(EMAIL);
        assertThat(member.getRole()).isEqualTo(MemberRole.USER);
        assertThat(member.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(member.getSocialAccounts()).singleElement().satisfies(account -> {
            assertThat(account.getIdentity()).isEqualTo(new SocialIdentity(OAuthProvider.KAKAO, "kakao-1"));
            assertThat(account.getConnectedAt()).isEqualTo(NOW);
            assertThat(account.getMember()).isSameAs(member);
        });
    }

    @Test
    @DisplayName("가입에 필요한 값이 하나라도 null이면 가입할 수 없다")
    void registerRejectsNull() {
        assertThatThrownBy(() -> Member.register(null, NOW))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Member.register(new MemberRegisterInfo(null, "테스트회원", EMAIL), NOW))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Member.register(
            new MemberRegisterInfo(KAKAO_1, null, EMAIL), NOW))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Member.register(
            new MemberRegisterInfo(KAKAO_1, "테스트회원", null), NOW))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Member.register(
            new MemberRegisterInfo(KAKAO_1, "테스트회원", EMAIL), null))
            .isInstanceOf(NullPointerException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " "})
    @DisplayName("닉네임이나 이메일이 비어 있으면 가입할 수 없다")
    void registerRejectsBlank(String blank) {
        assertThatThrownBy(() -> Member.register(
            new MemberRegisterInfo(KAKAO_1, blank, EMAIL), NOW))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Member.register(
            new MemberRegisterInfo(KAKAO_1, "테스트회원", blank), NOW))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"가", "가나다라마바사아자차카타파하가나다라마바사", " 하은맘", "하은맘 "})
    @DisplayName("닉네임이 2~20자가 아니거나 앞뒤에 공백이 있으면 가입할 수 없다")
    void registerRejectsNicknameLength(String nickname) {
        assertThatThrownBy(() -> Member.register(
            new MemberRegisterInfo(KAKAO_1, nickname, EMAIL), NOW))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"하은", "가나다라마바사아자차카타파하가나다라마바"})
    @DisplayName("닉네임이 정확히 2자이거나 20자여도 가입하고 바꿀 수 있다")
    void acceptsNicknameBoundaryLength(String nickname) {
        var registered = Member.register(new MemberRegisterInfo(KAKAO_1, nickname, EMAIL), NOW);
        var member = MemberFixture.kakaoMember();

        assertThat(registered.getNickname()).isEqualTo(nickname);
        assertThat(member.changeNickname(nickname)).isTrue();
        assertThat(member.getNickname()).isEqualTo(nickname);
    }

    @Test
    @DisplayName("닉네임을 바꾸면 true를 돌려준다")
    void changeNickname() {
        var member = MemberFixture.kakaoMember();

        assertThat(member.changeNickname("하은맘")).isTrue();
        assertThat(member.getNickname()).isEqualTo("하은맘");
    }

    @Test
    @DisplayName("지금 닉네임과 같으면 바꾸지 않고 false를 돌려준다")
    void changeToSameNickname() {
        var member = MemberFixture.kakaoMember();

        assertThat(member.changeNickname(MemberFixture.NICKNAME)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "가", "가나다라마바사아자차카타파하가나다라마바사", " 하은맘", "하은맘 "})
    @DisplayName("비어 있거나 2~20자가 아니거나 앞뒤에 공백이 있는 닉네임으로는 바꿀 수 없다")
    void changeNicknameRejectsInvalid(String nickname) {
        var member = MemberFixture.kakaoMember();

        assertThatThrownBy(() -> member.changeNickname(nickname))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(member.getNickname()).isEqualTo(MemberFixture.NICKNAME);
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
    @DisplayName("정지하면 SUSPENDED이고 로그인할 수 없으며 사유 · 관리자 · 시각이 저장된다")
    void suspend() {
        var member = MemberFixture.kakaoMember();

        member.suspend(ADMIN_ID, REASON, NOW);

        assertThat(member.getStatus()).isEqualTo(MemberStatus.SUSPENDED);
        assertThat(member.canLogin()).isFalse();
        assertThat(member.getSuspension()).isEqualTo(new MemberSuspension(REASON, ADMIN_ID, NOW));
    }

    @Test
    @DisplayName("이미 정지된 회원은 다시 정지할 수 없다")
    void suspendRejectsSuspended() {
        var member = MemberFixture.kakaoMember();
        member.suspend(ADMIN_ID, REASON, NOW);

        assertThatThrownBy(() -> member.suspend(ADMIN_ID + 1, "다른 사유", NOW.plusMinutes(1)))
            .isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.MEMBER_NOT_SUSPENDABLE));
        assertThat(member.getStatus()).isEqualTo(MemberStatus.SUSPENDED);
        assertThat(member.getSuspension()).isEqualTo(new MemberSuspension(REASON, ADMIN_ID, NOW));
    }

    @Test
    @DisplayName("탈퇴한 회원은 정지할 수 없다")
    void suspendRejectsWithdrawn() {
        var member = MemberFixture.withStatus(MemberStatus.WITHDRAWN);

        assertThatThrownBy(() -> member.suspend(ADMIN_ID, REASON, NOW))
            .isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.MEMBER_NOT_SUSPENDABLE));
        assertThat(member.getStatus()).isEqualTo(MemberStatus.WITHDRAWN);
        assertThat(member.getSuspension()).isNull();
    }

    @Test
    @DisplayName("관리자는 정지할 수 없다")
    void suspendRejectsAdmin() {
        var member = MemberFixture.withRole(MemberRole.ADMIN);

        assertThatThrownBy(() -> member.suspend(ADMIN_ID, REASON, NOW))
            .isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.MEMBER_NOT_SUSPENDABLE));
        assertStillActive(member);
    }

    @Test
    @DisplayName("정지한 관리자 · 사유 · 시각이 없으면 정지할 수 없다")
    void suspendRejectsNull() {
        var member = MemberFixture.kakaoMember();

        assertThatThrownBy(() -> member.suspend(null, REASON, NOW)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> member.suspend(ADMIN_ID, null, NOW)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> member.suspend(ADMIN_ID, REASON, null)).isInstanceOf(NullPointerException.class);
        assertStillActive(member);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    @DisplayName("정지 사유가 비어 있으면 정지할 수 없다")
    void suspendRejectsBlankReason(String reason) {
        var member = MemberFixture.kakaoMember();

        assertThatThrownBy(() -> member.suspend(ADMIN_ID, reason, NOW))
            .isInstanceOf(IllegalArgumentException.class);
        assertStillActive(member);
    }

    @Test
    @DisplayName("정지 사유는 200자까지다")
    void suspendRejectsLongReason() {
        var member = MemberFixture.kakaoMember();
        var maxReason = "가".repeat(MemberSuspension.REASON_MAX_LENGTH);

        assertThatThrownBy(() -> member.suspend(ADMIN_ID, maxReason + "가", NOW))
            .isInstanceOf(IllegalArgumentException.class);
        assertStillActive(member);
        member.suspend(ADMIN_ID, maxReason, NOW);
        assertThat(member.getSuspension().reason()).isEqualTo(maxReason);
    }

    @Test
    @DisplayName("관리자로 지정하면 ADMIN이고 토큰 역할에 ADMIN이 들어간다")
    void grantAdmin() {
        var member = MemberFixture.kakaoMember();

        member.grantAdmin();

        assertThat(member.getRole()).isEqualTo(MemberRole.ADMIN);
        assertThat(member.roles()).containsExactly(Role.BUYER, Role.ADMIN);
    }

    @ParameterizedTest
    @EnumSource(value = MemberStatus.class, names = {"SUSPENDED", "WITHDRAWN"})
    @DisplayName("정지 · 탈퇴한 회원은 관리자로 지정할 수 없다")
    void grantAdminRejectsInactive(MemberStatus status) {
        var member = MemberFixture.withStatus(status);

        assertThatThrownBy(member::grantAdmin)
            .isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.ADMIN_NOT_GRANTABLE));
        assertThat(member.getRole()).isEqualTo(MemberRole.USER);
    }

    @Test
    @DisplayName("이미 관리자인 회원은 다시 지정할 수 없다")
    void grantAdminRejectsAdmin() {
        var member = MemberFixture.withRole(MemberRole.ADMIN);

        assertThatThrownBy(member::grantAdmin)
            .isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.ADMIN_NOT_GRANTABLE));
        assertThat(member.getRole()).isEqualTo(MemberRole.ADMIN);
    }

    @Test
    @DisplayName("관리자 권한을 해제하면 USER이고 토큰 역할에 ADMIN이 빠진다")
    void revokeAdmin() {
        var member = MemberFixture.withRole(MemberRole.ADMIN);
        ReflectionTestUtils.setField(member, "id", 1L);

        member.revokeAdmin(ADMIN_ID);

        assertThat(member.getRole()).isEqualTo(MemberRole.USER);
        assertThat(member.roles()).containsExactly(Role.BUYER);
    }

    @Test
    @DisplayName("관리자가 아닌 회원은 해제할 수 없다")
    void revokeAdminRejectsUser() {
        var member = MemberFixture.kakaoMember();
        ReflectionTestUtils.setField(member, "id", 1L);

        assertThatThrownBy(() -> member.revokeAdmin(ADMIN_ID))
            .isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.ADMIN_NOT_REVOCABLE));
        assertThat(member.getRole()).isEqualTo(MemberRole.USER);
    }

    @Test
    @DisplayName("본인의 관리자 권한은 해제할 수 없다")
    void revokeAdminRejectsSelf() {
        var member = MemberFixture.withRole(MemberRole.ADMIN);
        ReflectionTestUtils.setField(member, "id", ADMIN_ID);

        assertThatThrownBy(() -> member.revokeAdmin(ADMIN_ID))
            .isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.ADMIN_NOT_REVOCABLE));
        assertThat(member.getRole()).isEqualTo(MemberRole.ADMIN);
    }

    @Test
    @DisplayName("해제하는 관리자가 없으면 해제할 수 없다")
    void revokeAdminRejectsNull() {
        var member = MemberFixture.withRole(MemberRole.ADMIN);

        assertThatThrownBy(() -> member.revokeAdmin(null)).isInstanceOf(NullPointerException.class);
        assertThat(member.getRole()).isEqualTo(MemberRole.ADMIN);
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

    @Test
    @DisplayName("판매자 상태를 처음 받으면 그대로 저장한다")
    void applySellerStateFirst() {
        Member member = MemberFixture.kakaoMember();

        member.applySellerState(SellerStatus.ACTIVE, NOW);

        assertThat(member.getSellerState().status()).isEqualTo(SellerStatus.ACTIVE);
        assertThat(member.getSellerState().changedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("더 늦게 일어난 판매자 상태 변경은 저장된 값을 바꾼다")
    void applySellerStateLater() {
        Member member = MemberFixture.withSellerState(SellerStatus.ACTIVE);

        member.applySellerState(SellerStatus.SUSPENDED, NOW.plusDays(1));

        assertThat(member.getSellerState().status()).isEqualTo(SellerStatus.SUSPENDED);
        assertThat(member.getSellerState().changedAt()).isEqualTo(NOW.plusDays(1));
    }

    @Test
    @DisplayName("저장된 값보다 이르거나 같은 시각의 판매자 상태 변경은 무시한다")
    void applySellerStateIgnoresEarlierOrEqual() {
        Member member = MemberFixture.withSellerState(SellerStatus.SUSPENDED);

        member.applySellerState(SellerStatus.ACTIVE, NOW.minusMinutes(1));
        member.applySellerState(SellerStatus.ACTIVE, NOW);

        assertThat(member.getSellerState().status()).isEqualTo(SellerStatus.SUSPENDED);
        assertThat(member.getSellerState().changedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("판매자 상태가 null이면 반영할 수 없다")
    void applySellerStateRejectsNull() {
        Member member = MemberFixture.kakaoMember();

        assertThatThrownBy(() -> member.applySellerState(null, NOW)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> member.applySellerState(SellerStatus.ACTIVE, null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("판매자 상태가 ACTIVE일 때만 활성 판매자다")
    void isActiveSeller() {
        assertThat(MemberFixture.kakaoMember().isActiveSeller()).isFalse();
        assertThat(MemberFixture.withSellerState(SellerStatus.SUSPENDED).isActiveSeller()).isFalse();
        assertThat(MemberFixture.withSellerState(SellerStatus.ACTIVE).isActiveSeller()).isTrue();
    }

    @Test
    @DisplayName("판매자 상태 복사본이 없으면 sellerStatus는 null이다")
    void sellerStatusOfPlainMember() {
        assertThat(MemberFixture.kakaoMember().sellerStatus()).isNull();
    }

    @ParameterizedTest
    @EnumSource(value = SellerStatus.class, names = {"ACTIVE", "SUSPENDED"})
    @DisplayName("반영된 판매자 상태를 sellerStatus로 돌려준다")
    void sellerStatusOfSeller(SellerStatus status) {
        assertThat(MemberFixture.withSellerState(status).sellerStatus()).isEqualTo(status);
    }

    @Test
    @DisplayName("일반 회원의 역할은 BUYER뿐이다")
    void rolesOfPlainMember() {
        assertThat(MemberFixture.kakaoMember().roles()).containsExactly(Role.BUYER);
    }

    @Test
    @DisplayName("활성 판매자는 BUYER와 SELLER 역할을 가진다")
    void rolesOfActiveSeller() {
        assertThat(MemberFixture.withSellerState(SellerStatus.ACTIVE).roles())
            .containsExactly(Role.BUYER, Role.SELLER);
    }

    @Test
    @DisplayName("정지된 판매자는 SELLER 역할을 갖지 않는다")
    void rolesOfSuspendedSeller() {
        assertThat(MemberFixture.withSellerState(SellerStatus.SUSPENDED).roles()).containsExactly(Role.BUYER);
    }

    @Test
    @DisplayName("관리자는 BUYER와 ADMIN 역할을 가진다")
    void rolesOfAdmin() {
        assertThat(MemberFixture.withRole(MemberRole.ADMIN).roles()).containsExactly(Role.BUYER, Role.ADMIN);
    }

    @Test
    @DisplayName("판매자인 관리자는 BUYER, SELLER, ADMIN 순서로 역할을 가진다")
    void rolesOfSellerAdmin() {
        var member = MemberFixture.withRole(MemberRole.ADMIN);
        member.applySellerState(SellerStatus.ACTIVE, NOW);

        assertThat(member.roles()).containsExactly(Role.BUYER, Role.SELLER, Role.ADMIN);
    }

    private void assertStillActive(Member member) {
        assertThat(member.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(member.getSuspension()).isNull();
    }
}
