package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.RecommendKafkaMessages.TIMEOUT;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaChildAccount;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaChildAccountRepository;
import com.bbcc.kidly.shared.auth.event.ChildAccountChangedEvent;
import com.bbcc.kidly.support.AsyncWait;
import com.bbcc.kidly.support.KafkaIntegrationTest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
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

/**
 * 추천이 인증 토픽에서 자녀 계정 생성 · 삭제를 받아 자녀 계정 복사본에 반영하는지 확인한다.
 * 같은 토픽에는 다른 테스트의 메시지도 쌓이므로 계정 ID(이벤트의 키)를 테스트마다 새로 만든다.
 */
@KafkaIntegrationTest
class RecommendAuthKafkaIntegrationTest {

    private static final String AUTH_TOPIC = "kidly.auth.events";
    private static final String AUTH_DLT = "kidly.auth.events.recommend.dlt";
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 11, 20, 10, 0);

    @Autowired
    private RecommendReplicaChildAccountRepository childAccountRepository;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;

    @Autowired
    private KafkaContainer kafka;

    private RecommendKafkaMessages messages;

    private List<MessageListenerContainer> listeners;

    @BeforeEach
    void startListeners() {
        messages = new RecommendKafkaMessages(kafka.getBootstrapServers(), jsonMapper);
        listeners = RecommendKafkaMessages.startListeners(kafkaListenerEndpointRegistry);
    }

    @AfterEach
    void stopListeners() {
        listeners.forEach(MessageListenerContainer::stop);
    }

    @Test
    @DisplayName("자녀 계정 생성을 받아 복사본을 만들고, 삭제를 받으면 그 계정을 삭제로 표시한다")
    void applyChildAccountCreatedAndDeleted() throws Exception {
        Long accountId = newId();

        messages.send(AUTH_TOPIC, accountChanged(accountId, false, CREATED_AT));
        AsyncWait.until(TIMEOUT, () -> account(accountId).isPresent());
        assertThat(account(accountId)).get().satisfies(account -> {
            assertThat(account.getChildProfileId()).isEqualTo(11L);
            assertThat(account.childProfileIdFor(1L)).contains(11L);
        });

        messages.send(AUTH_TOPIC, accountChanged(accountId, true, CREATED_AT.plusDays(1)));
        AsyncWait.until(TIMEOUT, () -> account(accountId).map(RecommendReplicaChildAccount::isDeleted).orElse(false));
    }

    @Test
    @DisplayName("인증 토픽에서 추천이 받지 않는 토큰 무효화는 건너뛰고 DLT로 보내지 않으며, 뒤 메시지를 처리한다")
    void skipTokensRevoked() throws Exception {
        Long accountId = newId();
        String key = String.valueOf(accountId);
        messages.sendRaw(AUTH_TOPIC, key, "auth.tokens-revoked", UUID.randomUUID(), "받지 않는 종류라 읽지 않는 본문");

        // 키가 같아 같은 파티션이므로, 뒤 메시지를 처리했으면 앞 메시지는 이미 처리됐다
        messages.send(AUTH_TOPIC, accountChanged(accountId, false, CREATED_AT));

        AsyncWait.until(TIMEOUT, () -> account(accountId).isPresent());
        assertThat(messages.deadLetters(AUTH_DLT, key)).isEmpty();
    }

    @Test
    @DisplayName("인증 메시지에 eventType 헤더가 없으면 인증 DLT로 보낸다")
    void deadLetterWithoutEventType() throws Exception {
        String key = String.valueOf(newId());

        messages.sendRaw(AUTH_TOPIC, key, null, UUID.randomUUID(), "어떤 이벤트인지 알 수 없는 본문");

        AsyncWait.until(TIMEOUT, () -> !messages.deadLetters(AUTH_DLT, key).isEmpty());
    }

    private Optional<RecommendReplicaChildAccount> account(Long accountId) {
        return childAccountRepository.findById(accountId);
    }

    private static ChildAccountChangedEvent accountChanged(Long accountId, boolean deleted, LocalDateTime at) {
        return new ChildAccountChangedEvent(UUID.randomUUID(), at,
            new ChildAccountChangedEvent.Data(accountId, 11L, 1L, deleted));
    }

    private static Long newId() {
        return ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
    }
}
