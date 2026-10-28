package com.bbcc.kidly.boundedcontext.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.notification.app.NotificationProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class NotificationPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withUserConfiguration(PropertiesOnly.class);

    @ParameterizedTest
    @ValueSource(strings = {"http://localhost:5173", "https://kidly.kro.kr"})
    @DisplayName("http(s) 주소면 바인딩된다")
    void validSiteUrl(String siteUrl) {
        runner.withPropertyValues("kidly.notification.site-url=" + siteUrl)
            .run(context -> assertThat(context.getBean(NotificationProperties.class).siteUrl()).isEqualTo(siteUrl));
    }

    @ParameterizedTest
    @ValueSource(strings = {"${NOTIFICATION_SITE_URL}", "kidly.kro.kr", "ftp://kidly.kro.kr", "https://kidly .kr"})
    @DisplayName("풀리지 않은 자리표시자나 http(s) 주소가 아닌 값이면 기동에 실패한다")
    void invalidSiteUrl(String siteUrl) {
        runner.withPropertyValues("kidly.notification.site-url=" + siteUrl)
            .run(context -> assertThat(context).getFailure()
                .hasStackTraceContaining("kidly.notification.site-url"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " "})
    @DisplayName("사이트 주소가 비어 있으면 기동에 실패한다")
    void blankSiteUrl(String siteUrl) {
        runner.withPropertyValues("kidly.notification.site-url=" + siteUrl)
            .run(context -> assertThat(context).getFailure()
                .hasStackTraceContaining("kidly.notification.site-url"));
    }

    @Test
    @DisplayName("사이트 주소가 없으면 기동에 실패한다")
    void missingSiteUrl() {
        runner.run(context -> assertThat(context).getFailure()
            .hasStackTraceContaining("kidly.notification"));
    }

    @Configuration
    @EnableConfigurationProperties(NotificationProperties.class)
    static class PropertiesOnly {
    }
}
