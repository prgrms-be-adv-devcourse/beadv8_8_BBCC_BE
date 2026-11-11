package com.bbcc.kidly.boundedcontext.notification.app;

import com.bbcc.kidly.boundedcontext.notification.app.port.NotificationDeadLetters;
import com.bbcc.kidly.boundedcontext.notification.domain.InboxRecipient;
import com.bbcc.kidly.boundedcontext.notification.domain.NotificationRepository;
import com.bbcc.kidly.boundedcontext.notification.domain.WithdrawnMemberRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 탈퇴한 회원의 알림 개인정보를 지체 없이 파기한다. 한 트랜잭션이고, 같은 탈퇴를 다시 받아도 결과가 같다.
 * 알림함 · 이메일 복사본 · 푸시 FID는 지운다(탈퇴 뒤에는 푸시가 가지 않는다). 이메일 발송 기록은 운영 감사용으로 상태 · 시각 · 유형을 남기고 받는 주소만 비우며,
 * 아직 보내지 않은 것은 SKIPPED로 끝내 다시 보내지 않는다.
 * 발송 중(SENDING)인 알림은 이미 주소를 읽어 보내는 중이라 결과 기록이 그대로 이어지고, 실패해 다시 맡으면 주소가 없어 SKIPPED로 끝난다.
 * 발송을 맡으려고 이미 읽은 알림은 여기서 버전을 올려 두어 맡는 쪽이 버전 충돌로 물러난다.
 * 탈퇴 표시를 영구히 남겨, 탈퇴 뒤에 처리되는 member.registered(늦게 온 것 · DLT 재처리)가 복사본을 되살리거나 환영 메일을 만들지 않게 한다.
 * 그 회원의 회원 토픽 DLT 원본(가입 이메일이 들어 있다)은 본문을 비운다. 행은 남고, 재처리하면 실패로 기록된다.
 * 탈퇴 뒤에 보관되는 원본은 저장할 때 본문을 두지 않는다(NotificationDeadLetters.insert).
 * 탈퇴(Kafka 스레드)와 가입 재처리 · DLT 보관(다른 스레드)이 엇갈려 복사본 · 본문이 남지 않게 회원 단위 잠금으로 줄 세운다.
 */
@Service
@RequiredArgsConstructor
public class MemberWithdrawalService {

    private final InboxNotificationService inboxNotificationService;
    private final NotificationRepository notificationRepository;
    private final MemberContactService memberContactService;
    private final PushTokenService pushTokenService;
    private final WithdrawnMemberRepository withdrawnMemberRepository;
    private final NotificationDeadLetters deadLetters;

    @Transactional
    public void purge(Long memberId) {
        withdrawnMemberRepository.lock(memberId);
        var now = LocalDateTime.now();
        inboxNotificationService.deleteAllOf(InboxRecipient.member(memberId));
        notificationRepository.skipUnsentOf(memberId, now);
        notificationRepository.eraseRecipientOf(memberId, now);
        memberContactService.delete(memberId);
        pushTokenService.deleteAllOf(memberId);
        withdrawnMemberRepository.markIfAbsent(memberId, now);
        deadLetters.erasePayloadOf(NotificationDeadLetters.MEMBER_EVENTS, String.valueOf(memberId), now);
    }

    /**
     * 회원 잠금을 잡고 탈퇴했는지 본다. 잠금은 호출한 트랜잭션이 끝날 때까지 쥐므로, 그 안에서 이어지는 가입 처리와 탈퇴가 엇갈리지 않는다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean lockAndCheckWithdrawn(Long memberId) {
        withdrawnMemberRepository.lock(memberId);
        return withdrawnMemberRepository.exists(memberId);
    }
}
