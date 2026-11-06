package com.bbcc.kidly.boundedcontext.notification.app;

import com.bbcc.kidly.boundedcontext.notification.domain.NotificationPolicy;
import com.bbcc.kidly.boundedcontext.notification.domain.PushTokenRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 브라우저의 푸시 대상 FID를 등록 · 해제 · 정리한다. 로그에 FID를 남기지 않는다.
 */
@Service
@RequiredArgsConstructor
public class PushTokenService {

    private final PushTokenRepository pushTokenRepository;

    /**
     * FID를 이 회원의 것으로 등록한다(멱등). 다른 회원의 FID면 주인을 바꾼다.
     * 회원당 상한을 넘으면 마지막 등록이 오래된 FID부터 지운다.
     */
    @Transactional
    public void register(Long memberId, String fid) {
        pushTokenRepository.register(memberId, fid, LocalDateTime.now());
        pushTokenRepository.deleteAllButNewest(memberId, NotificationPolicy.PUSH_FID_LIMIT_PER_MEMBER);
    }

    /**
     * 내 FID면 지운다(로그아웃). 없거나 다른 회원의 FID면 그대로 둔다.
     */
    @Transactional
    public void unregister(Long memberId, String fid) {
        pushTokenRepository.deleteByMemberIdAndFid(memberId, fid);
    }

    // 탈퇴한 회원의 FID를 모두 지운다. 없어도 된다
    @Transactional
    public void deleteAllOf(Long memberId) {
        pushTokenRepository.deleteByMemberId(memberId);
    }

    /**
     * 보존 기간(NotificationPolicy.PUSH_FID_RETENTION) 동안 다시 등록되지 않은 FID를 지운다. 지운 개수.
     */
    @Transactional
    public int deleteExpired() {
        return pushTokenRepository.deleteRegisteredBefore(
            LocalDateTime.now().minus(NotificationPolicy.PUSH_FID_RETENTION));
    }
}
