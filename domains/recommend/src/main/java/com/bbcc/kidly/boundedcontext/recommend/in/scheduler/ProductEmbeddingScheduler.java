package com.bbcc.kidly.boundedcontext.recommend.in.scheduler;

import com.bbcc.kidly.boundedcontext.recommend.app.embedding.ProductEmbeddingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 글이 바뀐 상품을 주기마다 임베딩한다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "spring.ai.model.embedding", havingValue = "openai", matchIfMissing = true)
@RequiredArgsConstructor
public class ProductEmbeddingScheduler {

    private final ProductEmbeddingService embeddingService;

    // fixedDelay는 이전 실행이 끝난 뒤부터 주기를 재므로 실행이 겹치지 않는다.
    // 기동 직후 다른 초기화와 겹치지 않게 첫 실행도 한 주기 뒤로 미룬다
    @Scheduled(fixedDelayString = "${kidly.recommend.embedding.interval:PT1M}",
        initialDelayString = "${kidly.recommend.embedding.interval:PT1M}")
    public void embedStaleProducts() {
        try {
            embeddingService.embedStaleProducts();
        } catch (RuntimeException e) {
            // 예외 메시지와 스택 트레이스에는 DB 오류의 행 값이 들어갈 수 있어 예외 종류만 남긴다
            log.error("상품 임베딩 주기가 예외로 끝났습니다. error={}", e.getClass().getSimpleName());
        }
    }
}
