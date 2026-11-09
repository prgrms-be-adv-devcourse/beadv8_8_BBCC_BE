package com.bbcc.kidly.boundedcontext.notification.domain;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;

public interface NotificationRepository {

    Notification save(Notification notification);

    Optional<Notification> findById(Long id);

    boolean existsByEventIdAndChannel(UUID eventId, NotificationChannel channel);

    /**
     * 다시 보낼 알림의 ID. 다음 시도 시각이 된 FAILED, {@code pendingBefore} 전에 만들어져 아직 PENDING인 것,
     * {@code sendingBefore} 전에 맡아 아직 SENDING인 것. ID 순서.
     */
    List<Long> findRetryTargetIds(LocalDateTime now, LocalDateTime pendingBefore, LocalDateTime sendingBefore,
                                  Pageable pageable);

    /**
     * 이 회원의 아직 보내지 않은 알림(PENDING, 다음 시도 시각이 있는 FAILED)을 SKIPPED로 끝낸다(회원 탈퇴).
     * 발송 중(SENDING)인 것은 그대로 둔다. 버전을 올려, 이 변경 전에 읽은 쪽이 저장하면 버전 충돌이 난다. 바꾼 개수.
     */
    int skipUnsentOf(Long memberId, LocalDateTime now);

    /**
     * 이 회원 알림의 받는 주소와 본문(상호 · 정지 사유 · 상품 이름이 들어간다)을 비운다(회원 탈퇴).
     * 제목은 고정 문구라 남긴다. 상태 · 시각 · 유형도 운영 확인용으로 그대로다. 버전을 올린다. 바꾼 개수.
     */
    int eraseRecipientOf(Long memberId, LocalDateTime now);

    /**
     * 이 시각보다 먼저 만든 알림 중 끝난 것(SENT · SKIPPED, 더 시도하지 않는 FAILED)을 지운다. 지운 개수.
     */
    int deleteFinishedCreatedBefore(LocalDateTime cutoff);
}
