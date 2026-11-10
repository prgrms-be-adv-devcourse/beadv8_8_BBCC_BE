package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.app.port.ai.AiResponseStore;
import com.bbcc.kidly.boundedcontext.recommend.domain.ai.AiResponseKey;
import com.bbcc.kidly.boundedcontext.recommend.domain.ai.AiResponseType;
import com.bbcc.kidly.support.IntegrationTest;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * AI 응답 저장소가 실제 PostgreSQL에서 응답 JSON을 저장하고 지난 답을 찾아 주는 동작을 확인한다.
 */
@IntegrationTest
class AiResponseStoreIntegrationTest {

    private static final AiResponseKey KEY = new AiResponseKey("a".repeat(64));
    private static final AiResponseKey OTHER_KEY = new AiResponseKey("b".repeat(64));
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 10, 12, 0);

    private final JsonMapper jsonMapper = new JsonMapper();

    @Autowired
    private AiResponseStore store;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("저장한 응답 JSON을 같은 종류와 키로 다시 읽는다")
    void readSavedResponseJson() {
        String json = "{\"summary\":\"데일리 맨투맨\",\"productIds\":[3,1,2],\"score\":0.5}";
        store.save(AiResponseType.HOME_PREVIEW, KEY, json, NOW);

        Optional<String> found = store.findLatestSince(AiResponseType.HOME_PREVIEW, KEY, NOW.minusHours(24));

        assertThat(found).isPresent();
        assertThat(jsonMapper.readTree(found.get())).isEqualTo(jsonMapper.readTree(json));
    }

    @ParameterizedTest
    @CsvSource({
        "-1, true",
        "0, true",
        "1, false"
    })
    @DisplayName("기준 시각보다 먼저 만든 줄은 읽지 않고 같은 시각에 만든 줄은 읽는다")
    void readOnlyResponsesCreatedAtOrAfterCutoff(long cutoffOffsetSeconds, boolean expectedFound) {
        store.save(AiResponseType.HOME_PREVIEW, KEY, "{\"summary\":\"맨투맨\"}", NOW);

        Optional<String> found = store.findLatestSince(
            AiResponseType.HOME_PREVIEW, KEY, NOW.plusSeconds(cutoffOffsetSeconds));

        assertThat(found.isPresent()).isEqualTo(expectedFound);
    }

    @Test
    @DisplayName("같은 종류와 키로 여러 번 저장하면 가장 최근 줄을 읽고 이전 줄도 지우지 않는다")
    void readLatestResponseAndKeepOlderRows() {
        store.save(AiResponseType.HOME_PREVIEW, KEY, "{\"version\":\"oldest\"}", NOW.minusHours(2));
        store.save(AiResponseType.HOME_PREVIEW, KEY, "{\"version\":\"latest\"}", NOW);
        store.save(AiResponseType.HOME_PREVIEW, KEY, "{\"version\":\"middle\"}", NOW.minusHours(1));

        Optional<String> found = store.findLatestSince(AiResponseType.HOME_PREVIEW, KEY, NOW.minusHours(24));

        assertThat(found).isPresent();
        assertThat(jsonMapper.readTree(found.get()).get("version").asString()).isEqualTo("latest");
        assertThat(rowCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("종류가 다르거나 키가 다르면 저장한 응답을 읽지 않는다")
    void doNotReadResponseOfOtherTypeOrKey() {
        store.save(AiResponseType.HOME_PREVIEW, KEY, "{\"summary\":\"맨투맨\"}", NOW);

        Optional<String> otherType = store.findLatestSince(AiResponseType.BOUGHT_TOGETHER, KEY, NOW.minusHours(24));
        Optional<String> otherKey = store.findLatestSince(AiResponseType.HOME_PREVIEW, OTHER_KEY, NOW.minusHours(24));

        assertThat(otherType).isEmpty();
        assertThat(otherKey).isEmpty();
    }

    private int rowCount() {
        return jdbcTemplate.queryForObject("select count(*) from recommend.ai_response", Integer.class);
    }
}
