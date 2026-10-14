package com.bbcc.kidly.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.LoggerContext;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.Banner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * logback-spring.xml이 프로필에 따라 Logstash로 JSON 로그를 보내는지 확인한다.
 * 빈은 띄우지 않고 스프링 로깅 초기화만 거친 뒤, 가짜 Logstash(ServerSocket)가 받은 줄을 읽는다.
 */
class LogstashLoggingTest {

    private static final Logger LOG = LoggerFactory.getLogger(LogstashLoggingTest.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @TempDir
    private Path logPath;

    @AfterEach
    void restoreLogging() {
        startLogging("test", "localhost:5000");
    }

    @Test
    @DisplayName("로컬 프로필은 traceId와 앱 이름을 담은 JSON 한 줄을 Logstash로 보낸다")
    void sendsJsonInLocalProfile() throws IOException {
        try (ServerSocket logstash = fakeLogstash()) {
            startLogging("local", destination(logstash));
            String message = "logstash-probe-" + UUID.randomUUID();

            MDC.put("traceId", "trace-1");
            try {
                LOG.info(message);
            } finally {
                MDC.remove("traceId");
            }

            JsonNode sent = receive(logstash, message);
            assertThat(sent.get("traceId").asString()).isEqualTo("trace-1");
            assertThat(sent.get("app").asString()).isEqualTo("kidly");
            assertThat(sent.get("level").asString()).isEqualTo("INFO");
            assertThat(sent.get("logger_name").asString()).isEqualTo(LogstashLoggingTest.class.getName());
            assertThat(sent.has("LOG_PATH")).isFalse();
        }
    }

    @Test
    @DisplayName("운영 프로필도 Logstash로 보낸다")
    void sendsInProdProfile() {
        startLogging("prod", "localhost:5000");

        assertThat(rootAppender("LOGSTASH")).isTrue();
        assertThat(rootAppender("ASYNC_FILE")).isTrue();
    }

    @Test
    @DisplayName("테스트 프로필은 Logstash로 보내지 않는다")
    void skipsInTestProfile() {
        startLogging("test", "localhost:5000");

        assertThat(rootAppender("LOGSTASH")).isFalse();
        assertThat(rootAppender("CONSOLE")).isTrue();
    }

    @Test
    @DisplayName("Logstash에 연결되지 않아도 로그를 남기는 쪽이 막히지 않는다")
    void doesNotBlockWithoutLogstash() throws IOException {
        String closedPort = destination(fakeLogstashThenClose());
        startLogging("local", closedPort);

        long start = System.nanoTime();
        for (int i = 0; i < 10_000; i++) {
            LOG.info("logstash-unreachable-{}", i);
        }

        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(5));
    }

    private void startLogging(String profile, String destination) {
        try (var context = new SpringApplicationBuilder(EmptyConfig.class)
            .web(WebApplicationType.NONE)
            .bannerMode(Banner.Mode.OFF)
            .logStartupInfo(false)
            .profiles(profile)
            .run("--logging.logstash.destination=" + destination, "--logging.file.path=" + logPath,
                // 닫힌 Logstash로 못 보낸 로그를 기본 1분씩 기다리지 않게 한다
                "--logging.logstash.shutdown-grace-period=1 second")) {
            assertThat(context.isActive()).isTrue();
        }
    }

    private static boolean rootAppender(String name) {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        return context.getLogger(Logger.ROOT_LOGGER_NAME).getAppender(name) != null;
    }

    private static ServerSocket fakeLogstash() throws IOException {
        ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
        socket.setSoTimeout((int) Duration.ofSeconds(10).toMillis());
        return socket;
    }

    private static ServerSocket fakeLogstashThenClose() throws IOException {
        ServerSocket socket = fakeLogstash();
        socket.close();
        return socket;
    }

    private static String destination(ServerSocket socket) {
        return "127.0.0.1:" + socket.getLocalPort();
    }

    // 스프링이 남긴 다른 로그도 함께 오므로 찾는 메시지가 담긴 줄까지 읽는다
    private static JsonNode receive(ServerSocket logstash, String message) throws IOException {
        try (Socket client = logstash.accept()) {
            client.setSoTimeout((int) Duration.ofSeconds(10).toMillis());
            var reader = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                JsonNode node = JSON.readTree(line);
                if (message.equals(node.path("message").asString())) {
                    return node;
                }
            }
        }
        throw new AssertionError("Logstash가 메시지를 받지 못했습니다: " + message);
    }

    @Configuration(proxyBeanMethods = false)
    static class EmptyConfig {
    }
}
