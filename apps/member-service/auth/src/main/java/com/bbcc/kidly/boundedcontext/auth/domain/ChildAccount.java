package com.bbcc.kidly.boundedcontext.auth.domain;

import com.bbcc.kidly.member.support.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "child_account",
    schema = "auth",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_child_account_login_id", columnNames = "login_id"),
        @UniqueConstraint(name = "uk_child_account_child_profile_id", columnNames = "child_profile_id")})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChildAccount extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "login_id", nullable = false, length = 20)
    private String loginId;
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;
    @Column(name = "guardian_id", nullable = false)
    private Long guardianId;
    @Column(name = "child_profile_id", nullable = false)
    private Long childProfileId;

    public static ChildAccount create(String loginId, String hash, Long guardianId, Long childProfileId) {
        var account = new ChildAccount();
        account.loginId = loginId;
        account.passwordHash = hash;
        account.guardianId = guardianId;
        account.childProfileId = childProfileId;
        return account;
    }

    public void changePassword(String hash) {
        this.passwordHash = hash;
    }
}
