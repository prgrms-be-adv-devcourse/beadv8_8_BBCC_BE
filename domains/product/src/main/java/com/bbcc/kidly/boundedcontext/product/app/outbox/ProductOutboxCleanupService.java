package com.bbcc.kidly.boundedcontext.product.app.outbox;

import com.bbcc.kidly.boundedcontext.product.domain.outbox.ProductOutboxRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 보낸 메시지를 브로커 보존 기간(7일)과 같게 보관하고, 그보다 오래된 메시지를 지운다.
 * 아직 보내지 않은 메시지는 오래돼도 지우지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductOutboxCleanupService {

    private final ProductOutboxRepository outboxRepository;
    private final ProductOutboxProperties properties;

    // deleteSentBefore는 벌크 DELETE라 트랜잭션 안에서만 돈다. 리포지토리에는 트랜잭션을 붙이지 않으므로 여기서 경계를 정한다.
    @Transactional
    public void cleanUp() {
        int deleted = outboxRepository.deleteSentBefore(LocalDateTime.now().minus(properties.retention()));
        if (deleted > 0) {
            log.info("보낸 상품 아웃박스 메시지를 지웠습니다. count={}", deleted);
        }
    }
}
