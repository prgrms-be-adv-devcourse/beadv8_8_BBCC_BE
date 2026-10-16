package com.bbcc.kidly.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.springframework.boot.Banner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Configuration;

/**
 * logback-spring.xml이 프로필마다 어디로 로그를 남기는지 확인한다.
 * 빈은 띄우지 않고 스프링 로깅 초기화만 거친 뒤 root logger의 출력 대상(appender)을 본다.
 */
class LoggingProfileTest {

    @TempDir
    private Path logPath;

    @AfterEach
    void restoreLogging() {
        startLogging("test");
    }

    @Test
    @DisplayName("로컬 프로필은 콘솔에만 남긴다")
    void localLogsToConsole() {
        startLogging("local");

        assertThat(rootAppenders()).containsExactly("CONSOLE");
    }

    @Test
    @DisplayName("운영 프로필은 콘솔과 파일(일반 · 에러, 비동기)에 남긴다")
    void prodLogsToConsoleAndFiles() {
        startLogging("prod");

        assertThat(rootAppenders()).containsExactlyInAnyOrder("CONSOLE", "ASYNC_FILE", "ASYNC_ERROR_FILE");
    }

    @Test
    @DisplayName("테스트 프로필은 콘솔에만 남긴다")
    void testLogsToConsole() {
        startLogging("test");

        assertThat(rootAppenders()).containsExactly("CONSOLE");
    }

    private void startLogging(String profile) {
        try (var context = new SpringApplicationBuilder(EmptyConfig.class)
            .web(WebApplicationType.NONE)
            .bannerMode(Banner.Mode.OFF)
            .logStartupInfo(false)
            .profiles(profile)
            .run("--logging.file.path=" + logPath)) {
            assertThat(context.isActive()).isTrue();
        }
    }

    private static List<String> rootAppenders() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger root = context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        List<String> names = new ArrayList<>();
        for (Iterator<Appender<ILoggingEvent>> it = root.iteratorForAppenders(); it.hasNext(); ) {
            names.add(it.next().getName());
        }
        return names;
    }

    @Configuration(proxyBeanMethods = false)
    static class EmptyConfig {
    }
}
