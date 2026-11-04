package com.bbcc.kidly.boundedcontext.auth.domain;

import com.bbcc.kidly.member.support.entity.BaseTimeEntity;
import com.bbcc.kidly.member.support.error.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
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

    @Column(name = "disabled_at")
    private LocalDateTime disabledAt;

    @Column(name = "disabled_by")
    private Long disabledBy;

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

    public boolean disable(Long actorId, LocalDateTime now) {
        // 자신을 비활성화하지 못하게 해 관리자가 스스로 마지막 계정을 없애지 못한다.
        if (id.equals(actorId)) {
            throw new BusinessException(AuthErrorCode.ADMIN_NOT_DISABLABLE);
        }
        if (!isActive()) {
            return false;
        }
        status = AdminStatus.DISABLED;
        disabledAt = now;
        disabledBy = actorId;
        return true;
    }
}
