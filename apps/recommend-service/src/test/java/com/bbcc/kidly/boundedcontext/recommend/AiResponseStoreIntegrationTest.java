package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.app.port.ai.AiResponseStore;
import com.bbcc.kidly.boundedcontext.recommend.domain.ai.AiResponseKey;
import com.bbcc.kidly.boundedcontext.recommend.domain.ai.AiResponseType;
import com.bbcc.kidly.support.AsyncWait;
import com.bbcc.kidly.support.IntegrationTest;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * AI 답 저장소가 실제 Redis에 답을 보관 기간과 함께 저장하고, 같은 종류 · 키로 다시 읽는 동작을 확인한다.
 */
@IntegrationTest
class AiResponseStoreIntegrationTest {

    private static final AiResponseKey KEY = new AiResponseKey("a".repeat(64));
    private static final AiResponseKey OTHER_KEY = new AiResponseKey("b".repeat(64));
    private static final Duration DAY = Duration.ofHours(24);
    private static final String JSON = "{\"productIds\":[3,1,2],\"reason\":\"데일리 맨투맨\"}";

    @Autowired
    private AiResponseStore store;

    @Test
    @DisplayName("저장한 응답 JSON을 같은 종류와 키로 그대로 다시 읽는다")
    void readSavedResponseJson() {
        store.save(AiResponseType.HOME_PREVIEW, KEY, JSON, DAY);

        assertThat(store.find(AiResponseType.HOME_PREVIEW, KEY)).contains(JSON);
    }

    @Test
    @DisplayName("종류가 다르거나 키가 다르면 저장한 응답을 읽지 않는다")
    void skipOtherTypeOrKey() {
        store.save(AiResponseType.HOME_PREVIEW, KEY, JSON, DAY);

        assertThat(store.find(AiResponseType.BOUGHT_TOGETHER, KEY)).isEmpty();
        assertThat(store.find(AiResponseType.HOME_PREVIEW, OTHER_KEY)).isEmpty();
    }

    @Test
    @DisplayName("같은 종류와 키로 다시 저장하면 새 응답을 읽는다")
    void replaceWithNewerResponse() {
        String newer = "{\"productIds\":[1],\"reason\":\"새 답\"}";
        store.save(AiResponseType.HOME_PREVIEW, KEY, JSON, DAY);
        store.save(AiResponseType.HOME_PREVIEW, KEY, newer, DAY);

        assertThat(store.find(AiResponseType.HOME_PREVIEW, KEY)).contains(newer);
    }

    @Test
    @DisplayName("보관 기간이 지나면 저장한 응답을 읽지 않는다")
    void forgetAfterTimeToLive() {
        store.save(AiResponseType.HOME_PREVIEW, KEY, JSON, Duration.ofSeconds(1));

        AsyncWait.until(Duration.ofSeconds(5), () -> store.find(AiResponseType.HOME_PREVIEW, KEY).isEmpty());

        assertThat(store.find(AiResponseType.HOME_PREVIEW, KEY)).isEmpty();
    }
}
