package com.bbcc.kidly.boundedcontext.order.domain.deadletter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReceivedDeadLetterTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 31, 10, 0);

    @Test
    @DisplayName("칸 길이를 넘는 값은 잘라 저장한다(DLT 저장이 길이 때문에 실패하지 않게): 예외 메시지 1000자, 예외 종류 300자, 스택 앞 10줄")
    void truncatesLongValues() {
        String stack = IntStream.rangeClosed(1, 15).mapToObj(i -> "\tat line" + i).collect(Collectors.joining("\n"));

        ReceivedDeadLetter letter = received("x".repeat(1200), "c".repeat(400), stack, "k".repeat(150));

        assertThat(letter.exceptionMessage()).hasSize(1000);
        assertThat(letter.exceptionClass()).hasSize(300);
        assertThat(letter.stackTraceHead().lines().toList()).hasSize(10).first().isEqualTo("\tat line1");
        assertThat(letter.messageKey()).hasSize(100);
    }

    @Test
    @DisplayName("짧은 값 · null은 그대로 둔다")
    void keepsShortAndNullValues() {
        ReceivedDeadLetter letter = received(null, "java.lang.IllegalStateException", "one\ntwo", null);

        assertThat(letter.exceptionMessage()).isNull();
        assertThat(letter.exceptionClass()).isEqualTo("java.lang.IllegalStateException");
        assertThat(letter.stackTraceHead()).isEqualTo("one\ntwo");
        assertThat(letter.messageKey()).isNull();
    }

    private static ReceivedDeadLetter received(String message, String exceptionClass, String stack, String key) {
        return new ReceivedDeadLetter("kidly.payment.events", 1, 7L, key, "payment.completed", "e-1", "{}",
            null, null, null, null, null, OrderDeadLetter.FailureType.PROCESS_FAILED, exceptionClass, message,
            stack, NOW);
    }
}
