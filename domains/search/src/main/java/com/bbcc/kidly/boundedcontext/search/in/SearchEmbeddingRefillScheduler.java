package com.bbcc.kidly.boundedcontext.search.in;

import com.bbcc.kidly.boundedcontext.search.app.SearchIndexService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 임베딩 API를 쓸 수 없어 벡터 없이 저장된 판매중 상품의 벡터를 주기마다 다시 만든다.
 * 상품 다시 발행 없이 API가 돌아오면 저절로 채워진다. 의미 검색을 끈 환경은 아무것도 하지 않는다.
 * Pod 두 개가 잠시 함께 돌아도(롤링 배포) 같은 벡터를 같은 버전으로 저장하므로 결과는 같다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SearchEmbeddingRefillScheduler {

    private final SearchIndexService searchIndexService;

    // fixedDelay는 이전 실행이 끝난 뒤부터 주기를 재므로 실행이 겹치지 않는다. 기동 직후를 피해 첫 실행도 한 주기 뒤로 미룬다
    @Scheduled(fixedDelayString = "PT1M", initialDelayString = "PT1M")
    public void refillMissingEmbeddings() {
        try {
            int refilled = searchIndexService.refillMissingEmbeddings();
            if (refilled > 0) {
                log.info("벡터 없는 상품의 벡터를 다시 만들었습니다. count={}", refilled);
            }
        } catch (RuntimeException e) {
            // 예외 메시지에는 Elasticsearch가 돌려준 문서 일부가 들어갈 수 있어 예외 종류만 남긴다
            log.warn("벡터 없는 상품을 다시 채우지 못했습니다. 다음 주기에 다시 합니다. error={}", e.getClass().getSimpleName());
        }
    }
}
