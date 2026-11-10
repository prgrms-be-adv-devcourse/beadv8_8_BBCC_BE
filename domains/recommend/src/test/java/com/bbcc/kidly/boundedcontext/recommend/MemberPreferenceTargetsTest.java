package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.bbcc.kidly.boundedcontext.recommend.app.port.preference.PreferenceSnapshotStore;
import com.bbcc.kidly.boundedcontext.recommend.app.port.preference.PreferenceSourceReader;
import com.bbcc.kidly.boundedcontext.recommend.app.preference.batch.MemberPreferenceTargets;
import com.bbcc.kidly.boundedcontext.recommend.domain.activity.ActivityType;
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
class MemberPreferenceTargetsTest {

    private static final LocalDateTime BASE_TIME = LocalDateTime.of(2026, 11, 10, 3, 0, 12);

    @Mock
    private PreferenceSourceReader sourceReader;

    @Mock
    private PreferenceSnapshotStore snapshotStore;

    private MemberPreferenceTargets targets;

    @BeforeEach
    void setUp() {
        targets = new MemberPreferenceTargets(sourceReader, snapshotStore);
    }

    @Test
    @DisplayName("PREF-11 대상 회원은 배치 기준 시각 31일 안에 자녀를 고르지 않은 활동이 있는 회원이다")
    void findTargetsWithinOneDayLongerWindow() {
        given(sourceReader.findMemberTargets(LocalDateTime.of(2026, 10, 10, 3, 0, 12), 7L, 100))
            .willReturn(List.of(8L, 9L));

        assertThat(targets.findIds(BASE_TIME, 7L, 100)).containsExactly(8L, 9L);
    }

    @Test
    @DisplayName("PREF-01 PREF-07 회원 활동은 배치 기준 시각 30일 전부터 배치 기준 시각까지 읽고, 구매확정은 읽지 않는다")
    void readMemberActivitiesUntilBaseTime() {
        LocalDateTime addedAt = BASE_TIME.minusDays(2);
        given(sourceReader.findMemberActivities(4L, LocalDateTime.of(2026, 10, 11, 3, 0, 12), BASE_TIME))
            .willReturn(List.of(new RecentActivity(ActivityType.CART_ITEM_ADDED, 1L, 10L, "브랜드A", addedAt, true)));

        assertThat(targets.findSignals(4L, BASE_TIME)).containsExactly(
            new PreferenceSignal(PreferenceSignalType.CART_ITEM_ADDED, addedAt, 10L, "브랜드A", 1L, true));
    }
}
