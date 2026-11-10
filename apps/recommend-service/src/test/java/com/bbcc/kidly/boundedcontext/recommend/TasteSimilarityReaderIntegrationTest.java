package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.bbcc.kidly.boundedcontext.recommend.app.port.preference.TasteSimilarityReader;
import com.bbcc.kidly.support.IntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 취향 벡터와 상품 벡터의 코사인 유사도를 실제 PostgreSQL(pgvector)로 재는 동작을 확인한다.
 */
@IntegrationTest
class TasteSimilarityReaderIntegrationTest {

    @Autowired
    private TasteSimilarityReader reader;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("유사도를 0 아래로 자르지 않고 그대로 읽으며(자르기는 계산 쪽이 한다) 벡터가 없는 상품은 결과에 없다")
    void measureCosineSimilarityWithoutClamping() {
        PreferenceTestData.insertProduct(jdbc, 100L, 10L, "Nike", PreferenceTestData.unitVector(0, 1f));
        PreferenceTestData.insertProduct(jdbc, 101L, 10L, "Nike", PreferenceTestData.unitVector(0, -1f));
        PreferenceTestData.insertProduct(jdbc, 102L, 10L, "Nike", PreferenceTestData.unitVector(1, 1f));
        PreferenceTestData.insertProduct(jdbc, 103L, 10L, "Nike", null);

        Map<Long, Double> similarities = reader.similarities(PreferenceTestData.unitVector(0, 1f),
            List.of(100L, 101L, 102L, 103L, 999L));

        assertThat(similarities).containsOnlyKeys(100L, 101L, 102L);
        assertThat(similarities.get(100L)).isCloseTo(1.0, within(1e-6));
        assertThat(similarities.get(101L)).isCloseTo(-1.0, within(1e-6));
        assertThat(similarities.get(102L)).isCloseTo(0.0, within(1e-6));
    }

    @Test
    @DisplayName("상품 ID가 비어 있으면 빈 Map을 읽는다")
    void readNoSimilaritiesForEmptyProductIds() {
        assertThat(reader.similarities(PreferenceTestData.unitVector(0, 1f), List.of())).isEmpty();
    }
}
