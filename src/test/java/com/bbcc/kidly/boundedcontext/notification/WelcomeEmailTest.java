package com.bbcc.kidly.boundedcontext.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.notification.app.WelcomeEmail;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WelcomeEmailTest {

    @Test
    @DisplayName("환영 메일은 정해진 제목과 사이트 링크가 담긴 본문이다")
    void compose() {
        var email = WelcomeEmail.compose("https://kidly.kro.kr");

        assertThat(email.title()).isEqualTo("[Kidly] 가입을 환영합니다");
        assertThat(email.body()).startsWith("Kidly에 오신 것을 환영합니다.").contains("https://kidly.kro.kr");
    }
}
