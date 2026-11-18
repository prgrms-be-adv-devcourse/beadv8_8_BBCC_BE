package com.bbcc.kidly.member.support.deadletter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DeadLetterMessageTest {

    @Test
    @DisplayName("헤더 값을 컬럼 길이로 자른다")
    void clipsHeaderValuesToColumnLength() {
        var message = message("t".repeat(150), "k".repeat(150), "e".repeat(150), "i".repeat(150), "f".repeat(300));

        assertThat(message.sourceTopic()).hasSize(100);
        assertThat(message.messageKey()).hasSize(100);
        assertThat(message.eventType()).hasSize(100);
        assertThat(message.eventId()).hasSize(100);
        assertThat(message.failureType()).hasSize(255);
    }

    @Test
    @DisplayName("NUL은 대체 문자로, 줄바꿈은 공백으로 바꾼다")
    void replacesNulAndLineBreaks() {
        var message = message("topic", "a\u0000b", "c\r\nd", "id", "type");

        assertThat(message.messageKey()).isEqualTo("a�b");
        assertThat(message.eventType()).isEqualTo("c  d");
    }

    @Test
    @DisplayName("없는 헤더는 null로 두고 본문은 바꾸지 않는다")
    void keepsNullAndPayload() {
        var message = new DeadLetterMessage("dlt", 0, 1L, "topic", null, null, null, null, null, "a\nb", null);

        assertThat(message.messageKey()).isNull();
        assertThat(message.failureType()).isNull();
        assertThat(message.payload()).isEqualTo("a\nb");
    }

    private static DeadLetterMessage message(String sourceTopic, String key, String eventType, String eventId,
        String failureType) {
        return new DeadLetterMessage("dlt", 0, 1L, sourceTopic, 0, 1L, key, eventType, eventId, "{}", failureType);
    }
}
