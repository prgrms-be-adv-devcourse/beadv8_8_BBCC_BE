package com.bbcc.kidly.boundedcontext.notification.app;

import com.bbcc.kidly.boundedcontext.notification.domain.NotificationPolicy;
import com.bbcc.kidly.boundedcontext.notification.domain.PushTokenRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그에 FID를 남기지 않는다.
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

    @Transactional
    public void unregister(Long memberId, String fid) {
        pushTokenRepository.deleteByMemberIdAndFid(memberId, fid);
    }

    @Transactional
    public void deleteAllOf(Long memberId) {
        pushTokenRepository.deleteByMemberId(memberId);
    }

    @Transactional
    public int deleteExpired() {
        return pushTokenRepository.deleteRegisteredBefore(
            LocalDateTime.now().minus(NotificationPolicy.PUSH_FID_RETENTION));
    }
}
