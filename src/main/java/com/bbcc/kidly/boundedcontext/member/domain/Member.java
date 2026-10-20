package com.bbcc.kidly.boundedcontext.member.domain;

import com.bbcc.kidly.global.entity.BaseTimeEntity;
import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.global.security.Role;
import com.bbcc.kidly.shared.seller.domain.SellerStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회원. 소셜 계정으로 가입 · 로그인한다. 판매자 여부는 역할로 저장하지 않고, 이벤트로 받은 판매자 상태 복사본을 가진다.
 * 닉네임 규칙은 {@link Nickname}. 회원끼리 겹치지 않는지는 {@link NicknamePolicy}가 확인하고, DB 유니크 제약이 마지막으로 막는다.
 * 이메일은 가입 때 받고 바꾸지 않는다.
 * 상태는 ACTIVE → SUSPENDED / WITHDRAWN으로만 바뀐다(정지 해제 없음).
 * 탈퇴하면 닉네임 · 이메일을 지우고 소셜 계정 연결을 끊는다. 같은 소셜 계정으로 다시 가입하면 새 회원이 된다.
 */
@Entity
@Table(
    name = "member",
    schema = "member",
    uniqueConstraints = @UniqueConstraint(name = "uk_member_nickname", columnNames = "nickname"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 탈퇴한 회원은 null이다
    private String nickname;

    // 가입 때 받은 이메일. 이메일을 받기 전에 가입한 회원은 null이다
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MemberRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MemberStatus status;

    // 판매자가 아니면 null
    @Embedded
    private SellerState sellerState;

    // 정지된 회원만 값이 있다
    @Embedded
    private MemberSuspension suspension;

    // 탈퇴한 회원만 값이 있다
    private LocalDateTime withdrawnAt;

    // 나중에 같은 사람의 여러 소셜 계정을 한 회원으로 통합할 수 있게 1:N으로 둔다
    @OneToMany(mappedBy = "member", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SocialAccount> socialAccounts = new ArrayList<>();

    private Member(String nickname, String email) {
        this.nickname = nickname;
        this.email = email;
        this.role = MemberRole.USER;
        this.status = MemberStatus.ACTIVE;
    }

    public static Member register(MemberRegisterInfo info, LocalDateTime now) {
        Member member = new Member(info.nickname().value(), info.email());
        member.socialAccounts.add(new SocialAccount(member, info.identity(), now));
        return member;
    }

    // 닉네임이 바뀌면 true. 같은 값이면 바꾸지 않는다. 다른 회원과 겹치는지는 NicknamePolicy가 확인하고, DB 유니크 제약이 마지막으로 막는다
    public boolean changeNickname(Nickname nickname) {
        // 탈퇴한 회원은 닉네임을 바꿀 수 없다
        if (status == MemberStatus.WITHDRAWN) {
            throw new BusinessException(MemberErrorCode.LOGIN_NOT_ALLOWED);
        }
        if (this.nickname.equals(nickname.value())) {
            return false;
        }
        this.nickname = nickname.value();
        return true;
    }

    // ACTIVE 회원만 정지할 수 있다. 관리자는 정지하면 되돌릴 방법이 없어 막는다(해제 없음)
    public void suspend(Long adminId, String reason, LocalDateTime now) {
        if (status != MemberStatus.ACTIVE || isAdmin()) {
            throw new BusinessException(MemberErrorCode.MEMBER_NOT_SUSPENDABLE);
        }
        this.status = MemberStatus.SUSPENDED;
        this.suspension = new MemberSuspension(reason, adminId, now);
    }

    // ACTIVE이고 관리자가 아닌 회원만 탈퇴한다. 관리자는 권한을 해제한 뒤 탈퇴한다
    public void withdraw(LocalDateTime now) {
        if (status != MemberStatus.ACTIVE || isAdmin()) {
            throw new BusinessException(MemberErrorCode.MEMBER_NOT_WITHDRAWABLE);
        }
        this.status = MemberStatus.WITHDRAWN;
        this.withdrawnAt = now;
        this.nickname = null;
        this.email = null;
        this.socialAccounts.clear();
    }

    public boolean canLogin() {
        return status == MemberStatus.ACTIVE;
    }

    public boolean isAdmin() {
        return role == MemberRole.ADMIN;
    }

    // ACTIVE이고 관리자가 아닌 회원만 지정한다. 역할은 대상 회원이 토큰을 새로 받을 때 반영된다
    public void grantAdmin() {
        if (status != MemberStatus.ACTIVE || isAdmin()) {
            throw new BusinessException(MemberErrorCode.ADMIN_NOT_GRANTABLE);
        }
        this.role = MemberRole.ADMIN;
    }

    // 관리자만 해제한다. 본인은 해제할 수 없게 해 관리자가 모두 사라지는 일을 막는다
    // 바로 반영되도록 호출한 서비스가 Access Token을 무효화한다
    public void revokeAdmin(Long adminId) {
        if (!isAdmin() || adminId.equals(id)) {
            throw new BusinessException(MemberErrorCode.ADMIN_NOT_REVOCABLE);
        }
        this.role = MemberRole.USER;
    }

    // 판매자 상태 복사본을 갱신한다. 저장된 값보다 늦게 일어난 변경만 반영한다
    public void applySellerState(SellerStatus status, LocalDateTime occurredAt) {
        var changed = new SellerState(status, occurredAt);
        if (sellerState == null || sellerState.isOlderThan(changed)) {
            this.sellerState = changed;
        }
    }

    public boolean isActiveSeller() {
        return sellerState != null && sellerState.isActive();
    }

    // 판매자가 아니면 null. 마이페이지가 정지 상태를 보여 주는 데 쓴다
    public SellerStatus sellerStatus() {
        return sellerState == null ? null : sellerState.status();
    }

    // 토큰 · 내 정보 조회의 역할. 판매자 여부는 저장하지 않고 판매자 상태 복사본으로 계산한다
    public List<Role> roles() {
        var roles = new ArrayList<Role>();
        roles.add(Role.BUYER);
        if (isActiveSeller()) {
            roles.add(Role.SELLER);
        }
        if (isAdmin()) {
            roles.add(Role.ADMIN);
        }
        return List.copyOf(roles);
    }

    public List<SocialAccount> getSocialAccounts() {
        return Collections.unmodifiableList(socialAccounts);
    }
}
