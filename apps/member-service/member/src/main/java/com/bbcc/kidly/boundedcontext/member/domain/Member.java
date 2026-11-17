package com.bbcc.kidly.boundedcontext.member.domain;

import com.bbcc.kidly.member.support.entity.BaseTimeEntity;
import com.bbcc.kidly.member.support.error.BusinessException;
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

    private String nickname;

    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MemberStatus status;

    @Embedded
    private SellerState sellerState;

    @Embedded
    private MemberSuspension suspension;

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

    public boolean changeNickname(Nickname nickname) {
        if (status == MemberStatus.WITHDRAWN) {
            throw new BusinessException(MemberErrorCode.LOGIN_NOT_ALLOWED);
        }
        if (this.nickname.equals(nickname.value())) {
            return false;
        }
        this.nickname = nickname.value();
        return true;
    }

    public void suspend(Long adminId, String reason, LocalDateTime now) {
        if (status != MemberStatus.ACTIVE) {
            throw new BusinessException(MemberErrorCode.MEMBER_NOT_SUSPENDABLE);
        }
        this.status = MemberStatus.SUSPENDED;
        this.suspension = new MemberSuspension(reason, adminId, now);
    }

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

    public boolean requiresSellerWithdrawalCheck() {
        return sellerState != null && !sellerState.isClosed();
    }

    public SellerStatus sellerStatus() {
        return sellerState == null ? null : sellerState.status();
    }

    public List<Role> roles() {
        var roles = new ArrayList<Role>();
        roles.add(Role.BUYER);
        if (isActiveSeller()) {
            roles.add(Role.SELLER);
        }
        return List.copyOf(roles);
    }
}
