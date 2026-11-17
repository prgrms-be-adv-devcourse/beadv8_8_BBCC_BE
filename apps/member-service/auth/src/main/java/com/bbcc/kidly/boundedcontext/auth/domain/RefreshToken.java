package com.bbcc.kidly.boundedcontext.auth.domain;

import com.bbcc.kidly.member.support.entity.BaseTimeEntity;
import com.bbcc.kidly.shared.auth.PrincipalId;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.security.crypto.keygen.Base64StringKeyGenerator;
import org.springframework.security.crypto.keygen.StringKeyGenerator;

@Entity
@Table(
    name = "refresh_token",
    schema = "auth",
    uniqueConstraints = @UniqueConstraint(name = "uk_refresh_token_token_hash", columnNames = "token_hash"),
    indexes = {
        @Index(name = "idx_refresh_token_expires_at", columnList = "expires_at"),
        @Index(name = "idx_refresh_token_previous_token_hash", columnList = "previous_token_hash")})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken extends BaseTimeEntity {

    private static final StringKeyGenerator GENERATOR =
        new Base64StringKeyGenerator(Base64.getUrlEncoder().withoutPadding(), 32);

    private static final Duration REUSE_GRACE = Duration.ofSeconds(10);

    @EmbeddedId
    private RefreshTokenId id;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "previous_token_hash", length = 64)
    private String previousTokenHash;

    @Column(name = "rotated_at")
    private LocalDateTime rotatedAt;

    public static String generate() {
        return GENERATOR.generateKey();
    }

    public static String hashOf(String token) {
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256은 모든 JVM이 지원해야 한다", e);
        }
    }

    public PrincipalId principal() {
        return id.toPrincipalId();
    }

    public boolean isUsable(LocalDateTime now) {
        return now.isBefore(expiresAt);
    }

    public boolean isReused(LocalDateTime now) {
        return rotatedAt != null && now.isAfter(rotatedAt.plus(REUSE_GRACE));
    }
}
