package com.bbcc.kidly.boundedcontext.notification.app;

import com.bbcc.kidly.boundedcontext.notification.domain.InboxRecipient;
import com.bbcc.kidly.boundedcontext.notification.domain.NotificationRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 탈퇴한 회원의 알림 개인정보를 지체 없이 파기한다. 한 트랜잭션이고, 같은 탈퇴를 다시 받아도 결과가 같다.
 * 알림함 · 이메일 복사본 · 푸시 FID는 지운다(탈퇴 뒤에는 푸시가 가지 않는다). 이메일 발송 기록은 운영 감사용으로 상태 · 시각 · 유형을 남기고 받는 주소만 비우며,
 * 아직 보내지 않은 것은 SKIPPED로 끝내 다시 보내지 않는다.
 * 발송 중(SENDING)인 알림은 이미 주소를 읽어 보내는 중이라 결과 기록이 그대로 이어지고, 실패해 다시 맡으면 주소가 없어 SKIPPED로 끝난다.
 * 발송을 맡으려고 이미 읽은 알림은 여기서 버전을 올려 두어 맡는 쪽이 버전 충돌로 물러난다.
 */
@Service
@RequiredArgsConstructor
public class MemberWithdrawalService {

    private final InboxNotificationService inboxNotificationService;
    private final NotificationRepository notificationRepository;
    private final MemberContactService memberContactService;
    private final PushTokenService pushTokenService;

    @Transactional
    public void purge(Long memberId) {
        var now = LocalDateTime.now();
        inboxNotificationService.deleteAllOf(InboxRecipient.member(memberId));
        notificationRepository.skipUnsentOf(memberId, now);
        notificationRepository.eraseRecipientOf(memberId, now);
        memberContactService.delete(memberId);
        pushTokenService.deleteAllOf(memberId);
    }
}
