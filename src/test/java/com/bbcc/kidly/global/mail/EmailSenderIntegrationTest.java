package com.bbcc.kidly.global.mail;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class EmailSenderIntegrationTest {

    @Autowired
    private EmailSender emailSender;

    @Test
    @DisplayName("기본 설정(꺼짐)에서는 로그 발송기가 등록되고, 보내도 실패하지 않는다")
    void loggingByDefault() {
        assertThat(emailSender).isInstanceOf(LoggingEmailSender.class);

        emailSender.send(new Email("parent@kidly.dev", "제목", "본문"));
    }
}
