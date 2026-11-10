package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.bbcc.kidly.boundedcontext.recommend.app.port.companion.CompanionSimilarityReader;
import com.bbcc.kidly.support.IntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 보고 있는 상품과 후보 상품의 코사인 유사도를 실제 PostgreSQL(pgvector)로 재는 동작을 확인한다.
 */
@IntegrationTest
class CompanionSimilarityReaderIntegrationTest {

    private static final Long VIEWED_PRODUCT_ID = 1L;

    @Autowired
    private CompanionSimilarityReader reader;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("PAIR-05 보고 있는 상품과의 유사도(1 - 코사인 거리)를 자르지 않고 읽으며, 벡터가 없는 후보와 없는 상품은 결과에 없다")
    void measureSimilarityToViewedProductWithoutClamping() {
        PreferenceTestData.insertProduct(jdbc, VIEWED_PRODUCT_ID, 10L, "Nike", PreferenceTestData.unitVector(0, 1f));
        PreferenceTestData.insertProduct(jdbc, 100L, 20L, "Nike", PreferenceTestData.unitVector(0, 1f));
        PreferenceTestData.insertProduct(jdbc, 101L, 20L, "Nike", PreferenceTestData.unitVector(0, -1f));
        PreferenceTestData.insertProduct(jdbc, 102L, 20L, "Nike", PreferenceTestData.unitVector(1, 1f));
        PreferenceTestData.insertProduct(jdbc, 103L, 20L, "Nike", null);

        Map<Long, Double> similarities = reader.similarities(VIEWED_PRODUCT_ID, List.of(100L, 101L, 102L, 103L, 999L));

        assertThat(similarities).containsOnlyKeys(100L, 101L, 102L);
        assertThat(similarities.get(100L)).isCloseTo(1.0, within(1e-6));
        assertThat(similarities.get(101L)).isCloseTo(-1.0, within(1e-6));
        assertThat(similarities.get(102L)).isCloseTo(0.0, within(1e-6));
    }
}
