package com.bbcc.kidly.boundedcontext.member.domain;

import com.bbcc.kidly.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.security.crypto.keygen.Base64StringKeyGenerator;
import org.springframework.security.crypto.keygen.StringKeyGenerator;

/**
 * 회원의 Refresh Token. 회원당 1개이고(PK가 member_id), 원문 대신 SHA-256 해시만 저장한다.
 * 발급 · 교체는 동시 요청에도 한 행만 남도록 {@link RefreshTokenRepository}의 쿼리 한 번으로 한다.
 */
@Entity
@Table(
    name = "refresh_token",
    schema = "member",
    uniqueConstraints = @UniqueConstraint(name = "uk_refresh_token_token_hash", columnNames = "token_hash"),
    indexes = @Index(name = "idx_refresh_token_expires_at", columnList = "expires_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken extends BaseTimeEntity {

    // 원문은 의미 없는 256비트(32바이트) 랜덤 값이다. 쿠키에 그대로 넣을 수 있게 URL-safe Base64(43자)로 만든다
    private static final StringKeyGenerator GENERATOR =
        new Base64StringKeyGenerator(Base64.getUrlEncoder().withoutPadding(), 32);

    @Id
    @Column(name = "member_id")
    private Long memberId;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    public static String generate() {
        return GENERATOR.generateKey();
    }

    // 원문이 256비트 랜덤 값이라 salt나 느린 해시 없이 SHA-256으로 충분하다
    public static String hashOf(String token) {
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256은 모든 JVM이 지원해야 한다", e);
        }
    }

    public boolean isUsable(LocalDateTime now) {
        return now.isBefore(expiresAt);
    }
}
