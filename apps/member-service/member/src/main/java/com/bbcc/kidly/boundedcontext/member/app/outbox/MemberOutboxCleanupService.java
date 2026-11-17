package com.bbcc.kidly.boundedcontext.member.app.outbox;

import com.bbcc.kidly.boundedcontext.member.domain.outbox.MemberOutboxRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemberOutboxCleanupService {

    private static final Duration RETENTION = Duration.ofDays(7);

    private final MemberOutboxRepository outboxRepository;

    // deleteSentBefore는 벌크 DELETE라 트랜잭션 안에서만 돈다. 리포지토리에는 트랜잭션을 붙이지 않으므로 여기서 경계를 정한다.
    @Transactional
    public void cleanUp() {
        var deleted = outboxRepository.deleteSentBefore(LocalDateTime.now().minus(RETENTION));
        if (deleted > 0) {
            log.info("보낸 회원 아웃박스 메시지를 지웠습니다. count={}", deleted);
        }
    }
}
