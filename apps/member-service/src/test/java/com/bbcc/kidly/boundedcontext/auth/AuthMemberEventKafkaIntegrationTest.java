package com.bbcc.kidly.boundedcontext.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.auth.domain.SocialAccount;
import com.bbcc.kidly.boundedcontext.auth.domain.SocialAccountRepository;
import com.bbcc.kidly.shared.member.event.MemberWithdrawnEvent;
import com.bbcc.kidly.support.AsyncWait;
import com.bbcc.kidly.support.KafkaIntegrationTest;
import com.bbcc.kidly.support.KafkaMessages;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.json.JsonMapper;

@KafkaIntegrationTest
class AuthMemberEventKafkaIntegrationTest {

    private static final String TOPIC = "kidly.member.events";
    private static final String DLT = "kidly.member.events.auth.dlt";
    private static final String GROUP = "kidly.auth";
    private static final String LISTENER_ID = "authMemberEventListener";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private static final long RETRY_BACKOFF_TOTAL_MS = 3_000;

    @Autowired
    private SocialAccountRepository socialAccountRepository;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;

    @Autowired
    private KafkaContainer kafka;

    private final Long memberId = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
    private final Long otherMemberId = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);

    private KafkaMessages messages;
    private MessageListenerContainer listener;

    // 읽기 시작 위치가 latest라 파티션을 배정받기 전에 보낸 메시지는 받지 못한다
    @BeforeEach
    void startListener() {
        messages = new KafkaMessages(kafka.getBootstrapServers(), jsonMapper);
        listener = kafkaListenerEndpointRegistry.getListenerContainer(LISTENER_ID);
        listener.start();
        AsyncWait.until(TIMEOUT, () -> !listener.getAssignedPartitions().isEmpty());
    }

    // stop()은 처리 중인 메시지가 끝날 때까지 기다리므로 뒤이은 테이블 비우기와 겹치지 않는다
    @AfterEach
    void stopListener() {
        listener.stop();
    }

    @Test
    @DisplayName("탈퇴 이벤트를 Kafka로 받아 그 회원의 소셜 계정 연결을 모두 지운다")
    void unlinkOnWithdraw() throws Exception {
        link(memberId);
        var other = link(otherMemberId).getId();

        messages.send(TOPIC, memberWithdrawn());

        AsyncWait.until(TIMEOUT, () -> socialAccountRepository.findByMemberId(memberId).isEmpty());
        assertThat(socialAccountRepository.findByMemberId(otherMemberId))
            .extracting(SocialAccount::getId).containsExactly(other);
    }

    @Test
    @DisplayName("같은 탈퇴 이벤트를 두 번 받아도 결과는 한 번 받은 것과 같고 DLT로 보내지 않는다")
    void duplicateEvent() throws Exception {
        link(memberId);
        var other = link(otherMemberId).getId();
        var event = memberWithdrawn();

        messages.send(TOPIC, event);
        var second = messages.send(TOPIC, event);

        messages.awaitProcessed(GROUP, TOPIC, second);
        assertThat(socialAccountRepository.findByMemberId(memberId)).isEmpty();
        assertThat(socialAccountRepository.findByMemberId(otherMemberId))
            .extracting(SocialAccount::getId).containsExactly(other);
        assertThat(messages.deadLetters(DLT, String.valueOf(memberId))).isEmpty();
    }

    @Test
    @DisplayName("받는 종류인데 본문을 읽지 못하면 재시도 없이 인증 DLT로 보낸다")
    void deadLetterUnreadableBody() throws Exception {
        var key = String.valueOf(memberId);
        var body = "{\"data\":{\"memberId\":\"숫자 아님\"}}";

        var sent = messages.sendRaw(TOPIC, key, "member.withdrawn", UUID.randomUUID(), body);

        AsyncWait.until(TIMEOUT, () -> !messages.deadLetters(DLT, key).isEmpty());
        assertThat(messages.deadLetters(DLT, key)).singleElement().satisfies(deadLetter -> {
            assertThat(deadLetter.value()).isEqualTo(body);
            assertThat(deadLetter.timestamp() - sent.timestamp()).isLessThan(RETRY_BACKOFF_TOTAL_MS);
        });
    }

    private SocialAccount link(Long memberId) {
        return socialAccountRepository.save(SocialAccountFixture.linked(memberId, UUID.randomUUID().toString()));
    }

    private MemberWithdrawnEvent memberWithdrawn() {
        return new MemberWithdrawnEvent(UUID.randomUUID(), LocalDateTime.now(),
            new MemberWithdrawnEvent.Data(memberId));
    }
}
