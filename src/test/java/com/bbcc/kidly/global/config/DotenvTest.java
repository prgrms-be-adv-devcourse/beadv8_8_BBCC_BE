package com.bbcc.kidly.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.Banner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * 프로젝트 루트의 .env를 spring-dotenv로 읽는다.
 * 빈은 띄우지 않고 설정 파일과 .env만 읽은 Environment를 확인한다.
 */
class DotenvTest {

    @TempDir
    private Path directory;

    @Test
    @DisplayName("따옴표로 감싼 값은 따옴표를 벗겨서 읽는다")
    void unquotesValue() throws IOException {
        writeDotenv("KIDLY_DOTENV_PROBE=\"quoted-value\"");

        assertThat(environment().getProperty("KIDLY_DOTENV_PROBE")).isEqualTo("quoted-value");
    }

    @Test
    @DisplayName("같은 키가 환경 변수에 있으면 환경 변수 값을 쓴다")
    void environmentVariableWins() throws IOException {
        writeDotenv("PATH=from-dotenv");

        assertThat(environment().getProperty("PATH")).isEqualTo(System.getenv("PATH"));
    }

    @Test
    @DisplayName("테스트 프로필에서는 .env를 읽지 않는다")
    void ignoredInTestProfile() throws IOException {
        writeDotenv("KIDLY_DOTENV_PROBE=value");

        assertThat(environment("test").getProperty("KIDLY_DOTENV_PROBE")).isNull();
    }

    private void writeDotenv(String content) throws IOException {
        Files.writeString(directory.resolve(".env"), content);
    }

    private Environment environment(String... profiles) {
        try (var context = new SpringApplicationBuilder(EmptyConfig.class)
            .web(WebApplicationType.NONE)
            .bannerMode(Banner.Mode.OFF)
            .logStartupInfo(false)
            .profiles(profiles)
            .properties("springdotenv.directory=" + directory)
            .run()) {
            return context.getEnvironment();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class EmptyConfig {
    }
}
