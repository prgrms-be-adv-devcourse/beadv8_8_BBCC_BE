package com.bbcc.kidly.boundedcontext.search.app.port;

import java.util.List;
import java.util.Optional;

/**
 * 문장을 의미 검색 벡터로 바꾼다. 구현은 out(OpenAI 임베딩 API).
 * 키가 없어 꺼져 있거나 API가 실패 · 시간 초과면 비어 있다. 부르는 쪽은 벡터 없이(키워드로만) 이어 간다.
 * API가 연속으로 실패하면 구현은 잠시 부르지 않고 바로 비워서 돌려준다.
 * 문장에는 검색어가 들어갈 수 있어 구현은 문장을 로그에 남기지 않는다.
 */
public interface TextEmbedder {

    /**
     * 색인할 상품 문장을 벡터로 바꾼다.
     */
    Optional<List<Float>> embed(String text);

    /**
     * 검색어를 벡터로 바꾼다. 같은 검색어는 잠시 기억해 다시 부르지 않는다.
     */
    Optional<List<Float>> embedKeyword(String keyword);
}
