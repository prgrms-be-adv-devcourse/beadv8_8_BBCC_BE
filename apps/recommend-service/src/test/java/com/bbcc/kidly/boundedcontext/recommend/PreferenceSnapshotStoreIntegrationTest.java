package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import org.springframework.dao.DataAccessException;

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

    @Test
    @DisplayName("다시 바꾸면 이전 친밀도 줄이 남지 않는다")
    void replacingRemovesPreviousAffinityRows() {
        store.replaceChild(CHILD_ID, new PreferenceSnapshot(
            List.of(new CategoryAffinity(10L, 1.0), new CategoryAffinity(11L, 0.5)),
            List.of(new BrandAffinity("a-brand", 1.0)), null, CALCULATED_AT));

        store.replaceChild(CHILD_ID, new PreferenceSnapshot(
            List.of(new CategoryAffinity(12L, 0.75)),
            List.of(new BrandAffinity("b-brand", 0.25)), null, CALCULATED_AT.plusDays(1)));

        PreferenceSnapshot found = store.findChild(CHILD_ID);
        assertThat(found.categories()).containsExactly(new CategoryAffinity(12L, 0.75));
        assertThat(found.brands()).containsExactly(new BrandAffinity("b-brand", 0.25));
        assertThat(found.calculatedAt()).isEqualTo(CALCULATED_AT.plusDays(1));
    }

    @Test
    @DisplayName("취향 벡터를 null로 바꾸면 저장된 취향 벡터가 지워진다")
    void replacingWithNullTasteVectorDeletesStoredVector() {
        store.replaceChild(CHILD_ID, new PreferenceSnapshot(List.of(), List.of(),
            new TasteVector(PreferenceTestData.vectorOf(0.5f), 1.0, 1), CALCULATED_AT));

        store.replaceChild(CHILD_ID, new PreferenceSnapshot(
            List.of(new CategoryAffinity(10L, 1.0)), List.of(), null, CALCULATED_AT));

        assertThat(store.findChild(CHILD_ID).tasteVector()).isNull();
    }

    @Test
    @DisplayName("한 자녀를 바꿔도 같은 ID의 회원 줄은 그대로다")
    void replacingChildKeepsMemberWithSameId() {
        PreferenceSnapshot memberSnapshot = new PreferenceSnapshot(
            List.of(new CategoryAffinity(10L, 0.5)), List.of(new BrandAffinity("a-brand", 0.5)),
            new TasteVector(PreferenceTestData.vectorOf(0.5f), 2.0, 2), CALCULATED_AT);
        store.replaceMember(CHILD_ID, memberSnapshot);

        store.replaceChild(CHILD_ID, new PreferenceSnapshot(
            List.of(new CategoryAffinity(20L, 1.0)), List.of(), null, CALCULATED_AT));

        assertThat(store.findMember(CHILD_ID)).isEqualTo(memberSnapshot);
    }

    @Test
    @DisplayName("회원 스냅숏도 바꾸고 읽을 수 있다")
    void readMemberSnapshotAfterReplacing() {
        PreferenceSnapshot snapshot = new PreferenceSnapshot(
            List.of(new CategoryAffinity(10L, 0.5)), List.of(new BrandAffinity("a-brand", 0.5)),
            new TasteVector(PreferenceTestData.vectorOf(-0.5f), 2.0, 2), CALCULATED_AT);

        store.replaceMember(3L, snapshot);

        assertThat(store.findMember(3L)).isEqualTo(snapshot);
        assertThat(store.findChild(3L)).isEqualTo(PreferenceSnapshot.empty());
    }

    @Test
    @DisplayName("저장된 줄이 없으면 빈 스냅숏을 읽는다")
    void readEmptySnapshotWhenNothingStored() {
        assertThat(store.findChild(CHILD_ID)).isEqualTo(PreferenceSnapshot.empty());
        assertThat(store.findMember(CHILD_ID)).isEqualTo(PreferenceSnapshot.empty());
    }

    @Test
    @DisplayName("바꾸는 도중 넣기가 실패하면 예외가 나고 이전 스냅숏이 그대로 남는다")
    void keepPreviousSnapshotWhenInsertFails() {
        PreferenceSnapshot previous = new PreferenceSnapshot(
            List.of(new CategoryAffinity(10L, 0.5)), List.of(new BrandAffinity("a-brand", 0.5)),
            new TasteVector(PreferenceTestData.vectorOf(0.5f), 2.0, 2), CALCULATED_AT);
        store.replaceChild(CHILD_ID, previous);
        PreferenceSnapshot invalid = new PreferenceSnapshot(
            List.of(new CategoryAffinity(10L, 0.5), new CategoryAffinity(11L, 1.5)), List.of(), null,
            CALCULATED_AT.plusDays(1));

        assertThatThrownBy(() -> store.replaceChild(CHILD_ID, invalid)).isInstanceOf(DataAccessException.class);

        assertThat(store.findChild(CHILD_ID)).isEqualTo(previous);
    }
}
