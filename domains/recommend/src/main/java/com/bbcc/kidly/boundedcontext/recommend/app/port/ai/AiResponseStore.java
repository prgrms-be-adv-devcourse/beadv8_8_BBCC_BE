package com.bbcc.kidly.boundedcontext.recommend.app.port.ai;

import com.bbcc.kidly.boundedcontext.recommend.domain.ai.AiResponseKey;
import com.bbcc.kidly.boundedcontext.recommend.domain.ai.AiResponseType;
import java.time.Duration;
import java.util.Optional;

/**
 * AI가 만든 답을 보관 기간 동안 저장하고, 같은 입력으로 다시 물으면 지난 답을 찾아 주는 나가는 연결의 요청서다.
 */
public interface AiResponseStore {

    /**
     * 그 종류와 키로 저장한 응답 JSON을 읽는다. 보관 기간이 지났거나 저장한 적이 없으면 빈 값이다.
     */
    Optional<String> find(AiResponseType type, AiResponseKey key);

    /**
     * 응답 JSON을 보관 기간 동안 저장한다. 같은 종류와 키로 다시 저장하면 덮어쓰고 보관 기간도 새로 시작한다.
     */
    void save(AiResponseType type, AiResponseKey key, String responseJson, Duration timeToLive);
}
