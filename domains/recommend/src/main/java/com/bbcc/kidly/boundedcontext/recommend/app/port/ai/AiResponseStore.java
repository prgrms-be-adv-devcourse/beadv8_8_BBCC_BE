package com.bbcc.kidly.boundedcontext.recommend.app.port.ai;

import com.bbcc.kidly.boundedcontext.recommend.domain.ai.AiResponseKey;
import com.bbcc.kidly.boundedcontext.recommend.domain.ai.AiResponseType;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * AI가 만든 답을 저장하고, 같은 입력으로 다시 물으면 지난 답을 찾아 주는 나가는 연결의 요청서다.
 */
public interface AiResponseStore {

    /**
     * 그 종류와 키로 {@code createdAfter} 이후(같은 시각 포함)에 저장한 줄 중 가장 최근 줄의 응답 JSON을 읽는다.
     */
    Optional<String> findLatestSince(AiResponseType type, AiResponseKey key, LocalDateTime createdAfter);

    /**
     * 응답 JSON을 새 줄로 저장한다. 같은 종류와 키라도 덮어쓰지 않고 줄을 더한다.
     * JSON이 아닌 문자열이면 저장하지 않고 예외를 던진다.
     */
    void save(AiResponseType type, AiResponseKey key, String responseJson, LocalDateTime createdAt);

    /**
     * {@code cutoff}보다 먼저 만든 줄을 지운다. 같은 시각에 만든 줄은 남긴다.
     *
     * @return 지운 줄 수
     */
    int deleteCreatedBefore(LocalDateTime cutoff);
}
