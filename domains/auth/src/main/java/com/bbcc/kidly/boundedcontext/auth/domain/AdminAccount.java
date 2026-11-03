package com.bbcc.kidly.boundedcontext.auth.domain;

import com.bbcc.kidly.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.Locale;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "admin_account",
    schema = "auth",
    uniqueConstraints = @UniqueConstraint(name = "uk_admin_account_email", columnNames = "email"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminAccount extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AdminStatus status;

    @Column(name = "created_by")
    private Long createdBy;

    private AdminAccount(String email, String passwordHash, Long createdBy) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.status = AdminStatus.ACTIVE;
        this.createdBy = createdBy;
    }

    public static AdminAccount create(String email, String passwordHash, Long createdBy) {
        return new AdminAccount(normalizeEmail(email), passwordHash, createdBy);
    }

    public static String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    public boolean isActive() {
        return status == AdminStatus.ACTIVE;
    }
}
