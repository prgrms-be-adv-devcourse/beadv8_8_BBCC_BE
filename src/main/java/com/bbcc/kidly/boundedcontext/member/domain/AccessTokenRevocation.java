package com.bbcc.kidly.boundedcontext.member.domain;

import com.bbcc.kidly.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회원별 Access Token 무효화 기준 시각. 이 시각(초) 이전에 발급된 Access Token은 거절한다.
 * 회원당 1행이고, Access Token 유효 기간이 지나면 의미가 없어 지운다({@code AccessTokenRevocationScheduler}).
 */
@Entity
@Table(
    name = "access_token_revocation",
    schema = "member",
    indexes = @Index(name = "idx_access_token_revocation_revoked_at", columnList = "revoked_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccessTokenRevocation extends BaseTimeEntity {

    @Id
    @Column(name = "member_id")
    private Long memberId;

    @Column(name = "revoked_at", nullable = false)
    private LocalDateTime revokedAt;
}
