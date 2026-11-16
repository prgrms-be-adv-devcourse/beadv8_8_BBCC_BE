package com.bbcc.kidly.boundedcontext.seller.app.outbox;

import com.bbcc.kidly.boundedcontext.seller.domain.outbox.SellerOutboxRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 아직 보내지 않은 메시지는 오래돼도 지우지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SellerOutboxCleanupService {

    // 보낸 메시지를 남겨 두는 기간. 브로커의 보존 기간과 같다
    private static final Duration RETENTION = Duration.ofDays(7);

    private final SellerOutboxRepository outboxRepository;

    // deleteSentBefore는 벌크 DELETE라 트랜잭션 안에서만 돈다. 리포지토리에는 트랜잭션을 붙이지 않으므로 여기서 경계를 정한다.
    @Transactional
    public void cleanUp() {
        var deleted = outboxRepository.deleteSentBefore(LocalDateTime.now().minus(RETENTION));
        if (deleted > 0) {
            log.info("보낸 판매자 아웃박스 메시지를 지웠습니다. count={}", deleted);
        }
    }
}
