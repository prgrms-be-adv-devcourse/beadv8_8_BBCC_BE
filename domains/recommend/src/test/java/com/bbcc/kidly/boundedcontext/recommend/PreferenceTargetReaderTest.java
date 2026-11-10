package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.bbcc.kidly.boundedcontext.recommend.app.preference.batch.PreferenceTargetReader;
import com.bbcc.kidly.boundedcontext.recommend.app.preference.batch.PreferenceTargets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.infrastructure.item.ExecutionContext;

@ExtendWith(MockitoExtension.class)
class PreferenceTargetReaderTest {

    private static final LocalDateTime BASE_TIME = LocalDateTime.of(2026, 11, 10, 3, 0);

    @Mock
    private PreferenceTargets targets;

    @Test
    @DisplayName("PREF-11 마지막으로 읽은 ID 다음부터 페이지를 이어 읽고, 덜 찬 페이지 뒤에는 끝난다")
    void readPagesInOrder() {
        given(targets.findIds(BASE_TIME, 0, 2)).willReturn(List.of(1L, 2L));
        given(targets.findIds(BASE_TIME, 2, 2)).willReturn(List.of(5L));
        PreferenceTargetReader reader = new PreferenceTargetReader(targets, BASE_TIME, 2);
        reader.open(new ExecutionContext());

        assertThat(readAll(reader)).containsExactly(1L, 2L, 5L);
    }

    @Test
    @DisplayName("PREF-11 저장한 위치로 새 Reader를 열면 저장한 ID 다음부터 읽는다")
    void reopenFromSavedPosition() {
        given(targets.findIds(BASE_TIME, 0, 2)).willReturn(List.of(1L, 2L));
        given(targets.findIds(BASE_TIME, 2, 2)).willReturn(List.of(5L));
        PreferenceTargetReader first = new PreferenceTargetReader(targets, BASE_TIME, 2);
        ExecutionContext context = new ExecutionContext();
        first.open(context);
        first.read();
        first.read();
        first.update(context);

        PreferenceTargetReader restarted = new PreferenceTargetReader(targets, BASE_TIME, 2);
        restarted.open(context);

        assertThat(readAll(restarted)).containsExactly(5L);
    }

    @Test
    @DisplayName("PREF-11 미리 읽어 두고 아직 돌려주지 않은 대상은 다시 실행할 때 빠지지 않는다")
    void saveLastReturnedIdNotPrefetchedId() {
        given(targets.findIds(BASE_TIME, 0, 3)).willReturn(List.of(1L, 2L, 3L));
        given(targets.findIds(BASE_TIME, 1, 3)).willReturn(List.of(2L, 3L));
        PreferenceTargetReader first = new PreferenceTargetReader(targets, BASE_TIME, 3);
        ExecutionContext context = new ExecutionContext();
        first.open(context);
        first.read();
        first.update(context);

        PreferenceTargetReader restarted = new PreferenceTargetReader(targets, BASE_TIME, 3);
        restarted.open(context);

        assertThat(readAll(restarted)).containsExactly(2L, 3L);
    }

    private static List<Long> readAll(PreferenceTargetReader reader) {
        List<Long> ids = new ArrayList<>();
        Long id = reader.read();
        while (id != null) {
            ids.add(id);
            id = reader.read();
        }
        return ids;
    }
}
