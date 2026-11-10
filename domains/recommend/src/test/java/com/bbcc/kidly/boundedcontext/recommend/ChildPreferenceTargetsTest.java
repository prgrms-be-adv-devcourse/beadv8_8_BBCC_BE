package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.bbcc.kidly.boundedcontext.recommend.app.port.preference.PreferenceSnapshotStore;
import com.bbcc.kidly.boundedcontext.recommend.app.port.preference.PreferenceSourceReader;
import com.bbcc.kidly.boundedcontext.recommend.app.preference.batch.ChildPreferenceTargets;
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
}
