package com.bbcc.kidly.boundedcontext.notification.domain;

import com.bbcc.kidly.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회원 브라우저 하나의 FCM 대상 식별값 FID(Firebase Installation ID). FID는 유일하고, 같은 브라우저에 다른 회원이
 * 로그인해 등록하면 주인이 바뀐다.
 * 등록은 동시에 와도 한 건이 되도록 저장소가 DB에서 한 번에 넣거나 고친다(PushTokenRepository.register).
 */
@Entity
@Table(schema = "notification", name = "push_token")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PushToken extends BaseTimeEntity {

    public static final int FID_MAX_LENGTH = 1024;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long memberId;

    @Column(nullable = false, length = FID_MAX_LENGTH)
    private String fid;

    @Column(nullable = false)
    private LocalDateTime lastRegisteredAt;
}
