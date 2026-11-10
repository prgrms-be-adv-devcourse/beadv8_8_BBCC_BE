package com.bbcc.kidly.boundedcontext.recommend.app.port.embedding;

/**
 * 서버가 여러 대여도 상품 임베딩을 한 대만 실행하게 하는 잠금의 요청서다.
 */
public interface ProductEmbeddingRunLock {

    /**
     * 잠금을 잡고 work를 실행한 뒤 푼다.
     *
     * @return 잠금을 잡아 실행했으면 true, 다른 서버가 쥐고 있어 건너뛰었으면 false
     */
    boolean runExclusively(Runnable work);
}
