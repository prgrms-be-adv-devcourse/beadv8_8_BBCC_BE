package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.bbcc.kidly.boundedcontext.recommend.app.port.preference.PreferenceSnapshotStore;
import com.bbcc.kidly.boundedcontext.recommend.app.port.preference.PreferenceSourceReader;
import com.bbcc.kidly.boundedcontext.recommend.app.preference.batch.ChildPreferenceTargets;
import com.bbcc.kidly.boundedcontext.recommend.domain.activity.ActivityType;
import com.bbcc.kidly.boundedcontext.recommend.domain.preference.ConfirmedPurchase;
import com.bbcc.kidly.boundedcontext.recommend.domain.preference.PreferenceSignal;
import com.bbcc.kidly.boundedcontext.recommend.domain.preference.PreferenceSignalType;
import com.bbcc.kidly.boundedcontext.recommend.domain.preference.RecentActivity;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChildPreferenceTargetsTest {

    private static final LocalDateTime BASE_TIME = LocalDateTime.of(2026, 11, 10, 3, 0, 12);

    @Mock
    private PreferenceSourceReader sourceReader;

    @Mock
    private PreferenceSnapshotStore snapshotStore;

    private ChildPreferenceTargets targets;

    @BeforeEach
    void setUp() {
        targets = new ChildPreferenceTargets(sourceReader, snapshotStore);
    }

    @Test
    @DisplayName("PREF-11 대상 자녀는 배치 기준 시각 31일 안의 활동이나 181일 안의 구매확정이 있는 자녀다")
    void findTargetsWithinOneDayLongerWindows() {
        given(sourceReader.findChildTargets(LocalDateTime.of(2026, 10, 10, 3, 0, 12),
            LocalDateTime.of(2026, 5, 13, 3, 0, 12), 7L, 100)).willReturn(List.of(8L, 9L));

        assertThat(targets.findIds(BASE_TIME, 7L, 100)).containsExactly(8L, 9L);
    }

    @Test
    @DisplayName("PREF-01 PREF-16 자녀 활동은 배치 기준 시각 30일 전부터, 구매확정은 180일 전부터 배치 기준 시각까지 읽는다")
    void readActivitiesAndPurchasesUntilBaseTime() {
        LocalDateTime viewedAt = BASE_TIME.minusDays(3);
        LocalDateTime confirmedAt = BASE_TIME.minusDays(100);
        given(sourceReader.findChildActivities(4L, LocalDateTime.of(2026, 10, 11, 3, 0, 12), BASE_TIME))
            .willReturn(List.of(new RecentActivity(ActivityType.PRODUCT_VIEWED, 1L, 10L, "브랜드A", viewedAt, false)));
        given(sourceReader.findChildPurchases(4L, LocalDateTime.of(2026, 5, 14, 3, 0, 12), BASE_TIME))
            .willReturn(List.of(new ConfirmedPurchase(2L, "브랜드B", confirmedAt)));

        assertThat(targets.findSignals(4L, BASE_TIME)).containsExactly(
            new PreferenceSignal(PreferenceSignalType.PRODUCT_VIEWED, viewedAt, 10L, "브랜드A", 1L, false),
            new PreferenceSignal(PreferenceSignalType.PURCHASE_CONFIRMED, confirmedAt, null, "브랜드B", 2L, false));
    }
}
