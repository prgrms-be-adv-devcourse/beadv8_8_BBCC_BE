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
