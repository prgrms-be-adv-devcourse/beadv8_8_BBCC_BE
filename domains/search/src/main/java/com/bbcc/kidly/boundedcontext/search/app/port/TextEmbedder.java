package com.bbcc.kidly.boundedcontext.search.app.port;

import java.util.List;
import java.util.Optional;

/**
 * 문장을 의미 검색 벡터로 바꾼다. 구현은 out(OpenAI 임베딩 API).
 * 키가 없어 꺼져 있거나 API가 실패 · 시간 초과면 비어 있다. 부르는 쪽은 벡터 없이(키워드로만) 이어 간다.
 * 문장에는 검색어가 들어갈 수 있어 구현은 문장을 로그에 남기지 않는다.
 */
public interface TextEmbedder {

    Optional<List<Float>> embed(String text);
}
