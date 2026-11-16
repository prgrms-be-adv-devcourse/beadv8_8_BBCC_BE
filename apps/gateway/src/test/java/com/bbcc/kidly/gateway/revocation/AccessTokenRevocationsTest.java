package com.bbcc.kidly.gateway.revocation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.shared.auth.PrincipalId;
import java.time.Duration;
import java.time.Instant;
import java.io.IOException;
import java.net.ServerSocket;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

@ExtendWith(OutputCaptureExtension.class)
class AccessTokenRevocationsTest {

    private static final PrincipalId MEMBER = PrincipalId.member(987_654_321L);

    private final LettuceConnectionFactory connectionFactory = connectionFactory();
    private final AccessTokenRevocations revocations =
        new AccessTokenRevocations(new ReactiveStringRedisTemplate(connectionFactory), Duration.ofMinutes(31));

    @AfterEach
    void close() {
        connectionFactory.destroy();
    }

    @Test
    @DisplayName("무효화를 확인하지 못하면 통과시키고 ERROR 로그를 남긴다. 로그에 주체를 넣지 않는다")
    void passesWhenRedisUnavailable(CapturedOutput output) {
        var now = Instant.now();

        assertThat(revocations.isRevoked(MEMBER, now.minusSeconds(60), now).block()).isFalse();
        assertThat(output.getOut()).contains("ERROR").doesNotContain(MEMBER.toSubject());
    }

    @Test
    @DisplayName("무효화를 쓰지 못하면 DataAccessException을 던진다(리스너가 DLT 대신 다시 시도한다)")
    void revokeThrowsWhenRedisUnavailable() {
        var now = Instant.now();

        assertThatThrownBy(() -> revocations.revoke(MEMBER, now, now)).isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("표식을 읽지 못하면 DataAccessException을 던진다(리스너가 되감기를 정한다)")
    void isLoadedThrowsWhenRedisUnavailable() {
        assertThatThrownBy(() -> revocations.isLoaded(0))
            .isInstanceOf(DataAccessException.class);
    }
    private static LettuceConnectionFactory connectionFactory() {
        try (var socket = new ServerSocket(0)) {
            var server = new RedisStandaloneConfiguration("127.0.0.1", socket.getLocalPort());
            var client = LettuceClientConfiguration.builder().commandTimeout(Duration.ofSeconds(1)).build();
            var factory = new LettuceConnectionFactory(server, client);
            factory.afterPropertiesSet();
            factory.start();
            return factory;
        } catch (IOException e) {
            throw new IllegalStateException("빈 포트를 찾지 못했습니다", e);
        }
    }
}
