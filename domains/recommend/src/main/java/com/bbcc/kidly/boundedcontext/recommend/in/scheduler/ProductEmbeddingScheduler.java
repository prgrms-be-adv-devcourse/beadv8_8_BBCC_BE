package com.bbcc.kidly.boundedcontext.recommend.in.scheduler;

import com.bbcc.kidly.boundedcontext.recommend.app.embedding.ProductEmbeddingService;
import com.bbcc.kidly.boundedcontext.recommend.app.port.embedding.ProductEmbeddingRunLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 글이 바뀐 상품을 주기마다 임베딩한다. 서버가 여러 대여도 잠금을 잡은 한 대만 실행한다.
 */
@Slf4j
@RequiredArgsConstructor
public class ProductEmbeddingScheduler {

    private final ProductEmbeddingRunLock runLock;
    private final ProductEmbeddingService embeddingService;

    // fixedDelay는 이전 실행이 끝난 뒤부터 주기를 재므로 실행이 겹치지 않는다. 첫 실행도 한 주기 뒤로 미룬다
    @Scheduled(fixedDelayString = "${kidly.recommend.embedding.interval:PT1M}",
        initialDelayString = "${kidly.recommend.embedding.interval:PT1M}")
    public void embedStaleProducts() {
        if (!runLock.runExclusively(embeddingService::embedStaleProducts)) {
            log.debug("다른 서버가 상품 임베딩을 실행 중이라 건너뜁니다");
        }
    }
}
