package com.bbcc.kidly.boundedcontext.childprofile.app.outbox;

import com.bbcc.kidly.boundedcontext.childprofile.domain.outbox.ChildProfileOutboxRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChildProfileOutboxCleanupService {

    private static final Duration RETENTION = Duration.ofDays(7);

    private final ChildProfileOutboxRepository outboxRepository;

    @Transactional
    public void cleanUp() {
        var deleted = outboxRepository.deleteSentBefore(LocalDateTime.now().minus(RETENTION));
        if (deleted > 0) {
            log.info("보낸 자녀 프로필 아웃박스 메시지를 지웠습니다. count={}", deleted);
        }
    }
}
