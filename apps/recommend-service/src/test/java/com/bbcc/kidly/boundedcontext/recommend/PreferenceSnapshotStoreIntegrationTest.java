package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.app.port.preference.PreferenceSnapshotStore;
import com.bbcc.kidly.boundedcontext.recommend.domain.preference.BrandAffinity;
import com.bbcc.kidly.boundedcontext.recommend.domain.preference.CategoryAffinity;
import com.bbcc.kidly.boundedcontext.recommend.domain.preference.PreferenceSnapshot;
import com.bbcc.kidly.boundedcontext.recommend.domain.preference.TasteVector;
import com.bbcc.kidly.support.IntegrationTest;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 선호 스냅숏 저장소가 실제 PostgreSQL(pgvector)에서 친밀도와 취향 벡터를 저장하고 읽는 동작을 확인한다.
 */
@IntegrationTest
class PreferenceSnapshotStoreIntegrationTest {

    private static final Long CHILD_ID = 7L;
    private static final LocalDateTime CALCULATED_AT = LocalDateTime.of(2026, 11, 10, 3, 0);

    @Autowired
    private PreferenceSnapshotStore store;

    @Test
    @DisplayName("자녀 스냅숏을 바꾼 뒤 읽으면 친밀도, 취향 벡터, 계산 시각이 그대로 나온다")
    void readChildSnapshotAfterReplacing() {
        PreferenceSnapshot snapshot = new PreferenceSnapshot(
            List.of(new CategoryAffinity(11L, 0.25), new CategoryAffinity(10L, 1.0)),
            List.of(new BrandAffinity("b-brand", 0.5), new BrandAffinity("a-brand", 0.0)),
            new TasteVector(PreferenceTestData.vectorOf(0.5f), 3.5, 4),
            CALCULATED_AT);

        store.replaceChild(CHILD_ID, snapshot);

        PreferenceSnapshot found = store.findChild(CHILD_ID);
        assertThat(found.categories()).containsExactly(new CategoryAffinity(10L, 1.0), new CategoryAffinity(11L, 0.25));
        assertThat(found.brands()).containsExactly(new BrandAffinity("a-brand", 0.0), new BrandAffinity("b-brand", 0.5));
        assertThat(found.tasteVector()).isEqualTo(snapshot.tasteVector());
        assertThat(found.calculatedAt()).isEqualTo(CALCULATED_AT);
    }
}
