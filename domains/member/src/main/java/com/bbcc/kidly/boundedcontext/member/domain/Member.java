package com.bbcc.kidly.boundedcontext.member.domain;

import com.bbcc.kidly.global.entity.BaseTimeEntity;
import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.shared.auth.Role;
import com.bbcc.kidly.shared.member.dto.MemberLoginState;
import com.bbcc.kidly.shared.seller.domain.SellerStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회원. 인증 도메인이 소셜 로그인 뒤 가입 키로 만든다. 판매자 여부는 역할로 저장하지 않고, 이벤트로 받은 판매자 상태 복사본을 가진다.
 * 닉네임 규칙은 {@link Nickname}. 회원끼리 겹치지 않는지는 {@link NicknamePolicy}가 확인하고, DB 유니크 제약이 마지막으로 막는다.
 * 이메일은 가입 때 받고 바꾸지 않는다.
 * 상태는 ACTIVE → SUSPENDED / WITHDRAWN으로만 바뀐다(정지 해제 없음).
 * 탈퇴하면 닉네임 · 이메일을 지운다. 소셜 계정 연결은 인증 도메인이 탈퇴 이벤트를 받아 끊는다.
 */
@Entity
@Table(
    name = "member",
    schema = "member",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_member_nickname", columnNames = "nickname"),
        @UniqueConstraint(name = "uk_member_registration_key", columnNames = "registration_key")})
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
    private MemberStatus status;

    // 판매자가 아니면 null
    @Embedded
    private SellerState sellerState;

    // 정지된 회원만 값이 있다
    @Embedded
    private MemberSuspension suspension;

    // 탈퇴한 회원만 값이 있다
    private LocalDateTime withdrawnAt;

    @Column(length = 64)
    private String registrationKey;

    private Member(String registrationKey, String nickname, String email) {
        this.registrationKey = registrationKey;
        this.nickname = nickname;
        this.email = email;
        this.status = MemberStatus.ACTIVE;
    }

    public static Member register(MemberRegisterInfo info) {
        return new Member(info.registrationKey(), info.nickname().value(), info.email());
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

    // ACTIVE 회원만 정지할 수 있다
    public void suspend(Long adminId, String reason, LocalDateTime now) {
        if (status != MemberStatus.ACTIVE) {
            throw new BusinessException(MemberErrorCode.MEMBER_NOT_SUSPENDABLE);
        }
        this.status = MemberStatus.SUSPENDED;
        this.suspension = new MemberSuspension(reason, adminId, now);
    }

    // ACTIVE 회원만 탈퇴할 수 있다
    public void withdraw(LocalDateTime now) {
        if (status != MemberStatus.ACTIVE) {
            throw new BusinessException(MemberErrorCode.MEMBER_NOT_WITHDRAWABLE);
        }
        this.status = MemberStatus.WITHDRAWN;
        this.withdrawnAt = now;
        this.nickname = null;
        this.email = null;
    }

    public MemberLoginState loginState() {
        return switch (status) {
            case ACTIVE -> new MemberLoginState.Active(id, nickname, roles());
            case SUSPENDED -> new MemberLoginState.Suspended(id);
            case WITHDRAWN -> new MemberLoginState.Withdrawn(id);
        };
    }

    // 판매자 상태 복사본을 갱신한다. 저장된 값보다 늦게 일어난 변경만 반영한다
    // SELLER 역할을 잃었으면(활성 판매자였다가 아니게 됐으면) true
    public boolean applySellerState(SellerStatus status, LocalDateTime occurredAt) {
        var wasActiveSeller = isActiveSeller();
        var changed = new SellerState(status, occurredAt);
        if (sellerState == null || sellerState.isOlderThan(changed)) {
            this.sellerState = changed;
        }
        return wasActiveSeller && !isActiveSeller();
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
        return List.copyOf(roles);
    }
}
