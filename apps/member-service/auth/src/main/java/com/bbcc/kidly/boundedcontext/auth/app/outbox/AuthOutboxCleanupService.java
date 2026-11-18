package com.bbcc.kidly.boundedcontext.auth.app.outbox;

import com.bbcc.kidly.boundedcontext.auth.domain.outbox.AuthOutboxRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthOutboxCleanupService {
    private static final Duration RETENTION = Duration.ofDays(7);

    private final AuthOutboxRepository repository;

    // deleteSentBefore는 벌크 DELETE라 트랜잭션 안에서만 돈다. 리포지토리에는 트랜잭션을 붙이지 않으므로 여기서 경계를 정한다.
    @Transactional
    public void cleanUp() {
        var deleted = repository.deleteSentBefore(LocalDateTime.now().minus(RETENTION));
        if (deleted > 0) {
            log.info("보낸 인증 아웃박스 메시지를 지웠습니다. count={}", deleted);
        }
    }
}
