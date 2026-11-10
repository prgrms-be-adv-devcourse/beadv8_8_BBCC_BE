package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.bbcc.kidly.boundedcontext.recommend.app.port.preference.PreferenceSnapshotStore;
import com.bbcc.kidly.boundedcontext.recommend.app.port.preference.PreferenceSourceReader;
import com.bbcc.kidly.boundedcontext.recommend.app.preference.batch.MemberPreferenceTargets;
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
}
