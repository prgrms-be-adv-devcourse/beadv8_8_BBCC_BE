package com.bbcc.kidly.boundedcontext.order.in.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.bbcc.kidly.boundedcontext.order.app.replica.ReplicaService;
import com.bbcc.kidly.boundedcontext.order.app.replica.dto.ChildChangedCommand;
import com.bbcc.kidly.shared.childprofile.domain.BodyType;
import com.bbcc.kidly.shared.childprofile.domain.Gender;
import com.bbcc.kidly.shared.childprofile.domain.PreferredFit;
import com.bbcc.kidly.shared.childprofile.event.ChildChangedEvent;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class ChildProfileEventListenerTest {

    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 10, 13, 9, 0);
    private static final BigDecimal HEIGHT = new BigDecimal("115.0");
    private static final BigDecimal WEIGHT = new BigDecimal("20.0");

    @Mock
    private ReplicaService replicaService;

    @InjectMocks
    private ChildProfileEventListener listener;

    @Captor
    private ArgumentCaptor<ChildChangedCommand> command;

    @Test
    @DisplayName("이벤트를 커맨드로 바꿔 넘긴다: 이름은 이벤트에 없어 null, 체형은 enum 이름")
    void convertsEvent() {
        listener.onChildChanged(event(BodyType.NORMAL, true));

        verify(replicaService).applyChildChanged(command.capture());
        assertThat(command.getValue()).isEqualTo(new ChildChangedCommand(
            1L, 1L, null, new BigDecimal("115.0"), new BigDecimal("20.0"), "NORMAL", true, OCCURRED_AT));
    }

    @Test
    @DisplayName("체형을 입력하지 않았으면 null로 넘긴다")
    void convertsNullBodyType() {
        listener.onChildChanged(event(null, false));

        verify(replicaService).applyChildChanged(command.capture());
        assertThat(command.getValue().bodyType()).isNull();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("eventsMissingRequiredValue")
    @DisplayName("필수 값(데이터 · 자녀 ID · 회원 ID · 발생 시각 · 키 · 몸무게 · 성별 · 생년월일)이 없는 변경 이벤트는 무시하고 WARN 한 줄")
    void skipsEventMissingRequiredValue(String reason, ChildChangedEvent event, CapturedOutput output) {
        listener.onChildChanged(event);

        verifyNoInteractions(replicaService);
        assertThat(output).containsPattern("WARN.*event=child.changed.*reason=" + reason);
    }

    static Stream<Arguments> eventsMissingRequiredValue() {
        return Stream.of(
            Arguments.of("missing-data", new ChildChangedEvent(UUID.randomUUID(), OCCURRED_AT, null)),
            Arguments.of("missing-child-id", new ChildChangedEvent(UUID.randomUUID(), OCCURRED_AT,
                data(null, 1L, HEIGHT, WEIGHT, false))),
            Arguments.of("missing-member-id", new ChildChangedEvent(UUID.randomUUID(), OCCURRED_AT,
                data(1L, null, HEIGHT, WEIGHT, false))),
            Arguments.of("missing-occurred-at", new ChildChangedEvent(UUID.randomUUID(), null,
                data(1L, 1L, HEIGHT, WEIGHT, false))),
            Arguments.of("missing-height", new ChildChangedEvent(UUID.randomUUID(), OCCURRED_AT,
                data(1L, 1L, null, WEIGHT, false))),
            Arguments.of("missing-weight", new ChildChangedEvent(UUID.randomUUID(), OCCURRED_AT,
                data(1L, 1L, HEIGHT, null, false))),
            Arguments.of("missing-gender", new ChildChangedEvent(UUID.randomUUID(), OCCURRED_AT,
                profile(null, LocalDate.of(2019, 1, 1), BodyType.NORMAL, PreferredFit.LOOSE))),
            Arguments.of("missing-birth-date", new ChildChangedEvent(UUID.randomUUID(), OCCURRED_AT,
                profile(Gender.MALE, null, BodyType.NORMAL, PreferredFit.LOOSE))));
    }

    @Test
    @DisplayName("체형 · 선호 핏은 계약상 비어도 되므로(null) 무시하지 않고 그대로 반영한다")
    void appliesWithoutBodyTypeAndPreferredFit(CapturedOutput output) {
        listener.onChildChanged(new ChildChangedEvent(UUID.randomUUID(), OCCURRED_AT,
            profile(Gender.MALE, LocalDate.of(2019, 1, 1), null, null)));

        verify(replicaService).applyChildChanged(command.capture());
        assertThat(command.getValue().bodyType()).isNull();
        assertThat(output).doesNotContain("event=child.changed");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("deleteEventsWithoutProfileValues")
    @DisplayName("키 · 몸무게 · 성별 · 생년월일이 없는 삭제 이벤트는 무시하지 않고 삭제 표시만 맡긴다(기존 값은 덮지 않음)")
    void deleteWithoutProfileValuesMarksDeleted(String missing, ChildChangedEvent.Data data) {
        listener.onChildChanged(new ChildChangedEvent(UUID.randomUUID(), OCCURRED_AT, data));

        verify(replicaService).applyChildDeleted(1L, OCCURRED_AT);
        verify(replicaService, never()).applyChildChanged(any());
    }

    static Stream<Arguments> deleteEventsWithoutProfileValues() {
        LocalDate birthDate = LocalDate.of(2019, 1, 1);
        return Stream.of(
            Arguments.of("키 · 몸무게 없음", new ChildChangedEvent.Data(1L, 1L, null, null, Gender.MALE, birthDate,
                BodyType.NORMAL, PreferredFit.LOOSE, true)),
            Arguments.of("성별 없음", new ChildChangedEvent.Data(1L, 1L, HEIGHT, WEIGHT, null, birthDate,
                BodyType.NORMAL, PreferredFit.LOOSE, true)),
            Arguments.of("생년월일 없음", new ChildChangedEvent.Data(1L, 1L, HEIGHT, WEIGHT, Gender.MALE, null,
                BodyType.NORMAL, PreferredFit.LOOSE, true)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("deleteEventsMissingRequiredValue")
    @DisplayName("삭제 이벤트도 자녀 ID · 회원 ID · 발생 시각이 없으면 무시하고 WARN 한 줄")
    void skipsDeleteEventMissingRequiredValue(String reason, ChildChangedEvent event, CapturedOutput output) {
        listener.onChildChanged(event);

        verifyNoInteractions(replicaService);
        assertThat(output).containsPattern("WARN.*event=child.changed.*reason=" + reason);
    }

    static Stream<Arguments> deleteEventsMissingRequiredValue() {
        return Stream.of(
            Arguments.of("missing-child-id", new ChildChangedEvent(UUID.randomUUID(), OCCURRED_AT,
                data(null, 1L, null, null, true))),
            Arguments.of("missing-member-id", new ChildChangedEvent(UUID.randomUUID(), OCCURRED_AT,
                data(1L, null, null, null, true))),
            Arguments.of("missing-occurred-at", new ChildChangedEvent(UUID.randomUUID(), null,
                data(1L, 1L, null, null, true))));
    }

    private static ChildChangedEvent event(BodyType bodyType, boolean deleted) {
        return new ChildChangedEvent(UUID.randomUUID(), OCCURRED_AT, new ChildChangedEvent.Data(
            1L, 1L, new BigDecimal("115.0"), new BigDecimal("20.0"), Gender.MALE, LocalDate.of(2019, 1, 1),
            bodyType, PreferredFit.LOOSE, deleted));
    }

    /**
     * 자녀 1 · 회원 1 · 키 · 몸무게가 있는 변경 이벤트에서 성별 · 생년월일 · 체형 · 선호 핏만 정한다.
     */
    private static ChildChangedEvent.Data profile(Gender gender, LocalDate birthDate, BodyType bodyType,
        PreferredFit preferredFit) {
        return new ChildChangedEvent.Data(1L, 1L, HEIGHT, WEIGHT, gender, birthDate, bodyType, preferredFit, false);
    }

    private static ChildChangedEvent.Data data(Long childId, Long memberId, BigDecimal height, BigDecimal weight,
        boolean deleted) {
        return new ChildChangedEvent.Data(childId, memberId, height, weight, Gender.MALE, LocalDate.of(2019, 1, 1),
            BodyType.NORMAL, PreferredFit.LOOSE, deleted);
    }
}
