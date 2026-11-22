package com.bbcc.kidly.boundedcontext.notification.domain;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository {

    Notification save(Notification notification);

    Optional<Notification> findById(Long id);

    boolean existsByEventIdAndChannel(UUID eventId, NotificationChannel channel);

    /**
     * 다시 보낼 알림. 다음 시도 시각이 된 FAILED, {@code pendingBefore} 전에 만들어져 아직 PENDING인 것,
     * {@code sendingBefore} 전에 맡아 아직 SENDING인 것. ID 순서.
     * 호출한 트랜잭션이 끝날 때까지 행을 잠그고, 다른 트랜잭션이 잠근 행은 건너뛴다(SKIP LOCKED).
     */
    List<Notification> lockRetryTargets(LocalDateTime now, LocalDateTime pendingBefore, LocalDateTime sendingBefore,
                                        int limit);

    /**
     * 이 회원의 아직 보내지 않은 알림(PENDING, 다음 시도 시각이 있는 FAILED)을 SKIPPED로 끝낸다(회원 탈퇴).
     * 발송 중(SENDING)인 것은 그대로 둔다. 버전을 올려, 이 변경 전에 읽은 쪽이 저장하면 버전 충돌이 난다.
     */
    int skipUnsentOf(Long memberId, LocalDateTime now);

    /**
     * 이 회원 알림의 받는 주소와 본문(상호, 정지 사유, 상품 이름이 들어간다)을 비운다(회원 탈퇴).
     * 제목은 고정 문구라 남긴다. 상태, 시각, 유형도 운영 확인용으로 그대로다. 버전을 올린다.
     */
    int eraseRecipientOf(Long memberId, LocalDateTime now);

    int deleteFinishedCreatedBefore(LocalDateTime cutoff);

    long countUnsent();

    Optional<LocalDateTime> findOldestUnsentCreatedAt();
}
