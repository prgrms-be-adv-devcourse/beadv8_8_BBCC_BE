package com.bbcc.kidly.boundedcontext.childprofile;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.childprofile.app.ChildProfileService;
import com.bbcc.kidly.boundedcontext.childprofile.domain.GuardianVerificationRepository;
import com.bbcc.kidly.boundedcontext.childprofile.app.dto.ChildProfileRequest;
import com.bbcc.kidly.boundedcontext.childprofile.domain.ChildProfile;
import com.bbcc.kidly.boundedcontext.childprofile.domain.ChildProfileRepository;
import com.bbcc.kidly.shared.childprofile.domain.BodyType;
import com.bbcc.kidly.shared.childprofile.domain.Gender;
import com.bbcc.kidly.shared.childprofile.domain.PreferredFit;
import com.bbcc.kidly.shared.event.DomainEvent;
import com.bbcc.kidly.shared.member.event.MemberSuspendedEvent;
import com.bbcc.kidly.shared.member.event.MemberWithdrawnEvent;
import com.bbcc.kidly.support.AsyncWait;
import com.bbcc.kidly.support.KafkaIntegrationTest;
import com.bbcc.kidly.support.KafkaMessages;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.json.JsonMapper;

@KafkaIntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class ChildProfileMemberEventKafkaIntegrationTest {
    @Autowired private GuardianVerificationRepository verifications;

    private static final String TOPIC = "kidly.member.events";
    private static final String DLT = "kidly.member.events.childprofile.dlt";
    private static final String GROUP = "kidly.childprofile";
    private static final String LISTENER_ID = "childProfileMemberEventListener";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final ChildProfileRequest REQUEST = new ChildProfileRequest(
        "첫째", LocalDate.of(2019, 1, 15), Gender.MALE, new BigDecimal("115.0"), new BigDecimal("20.0"),
        BodyType.NORMAL, PreferredFit.LOOSE, true, true);

    private static final long RETRY_BACKOFF_TOTAL_MS = 3_000;

    @Autowired
    private ChildProfileService childProfileService;

    @Autowired
    private ChildProfileRepository childProfileRepository;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;

    @Autowired
    private KafkaContainer kafka;

    private final Long memberId = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
    private final Long otherMemberId = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);

    private MessageListenerContainer listener;
    private KafkaMessages messages;

    // 읽기 시작 위치가 latest라 파티션을 배정받기 전에 보낸 메시지는 받지 못한다
    @BeforeEach
    void startListener() {
        messages = new KafkaMessages(kafka.getBootstrapServers(), jsonMapper);
        verifications.save(GuardianVerificationFixture.verification(memberId));
        verifications.save(GuardianVerificationFixture.verification(otherMemberId));
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
    @DisplayName("탈퇴 이벤트가 커밋되면 Kafka로 받아 그 회원의 자녀와 본인인증 기록을 모두 삭제한다")
    void deleteChildrenOnWithdraw() throws Exception {
        childProfileService.register(memberId, REQUEST);
        childProfileService.register(memberId, REQUEST);
        var other = childProfileService.register(otherMemberId, REQUEST).id();

        send(memberWithdrawn());

        AsyncWait.until(TIMEOUT, () -> childrenOf(memberId).isEmpty());
        assertThat(childrenOf(otherMemberId)).extracting(ChildProfile::getId).containsExactly(other);
        assertThat(verifications.existsByMemberId(memberId)).isFalse();
        assertThat(verifications.existsByMemberId(otherMemberId)).isTrue();
    }

    @Test
    @DisplayName("정지 이벤트를 받으면 그 회원의 자녀만 모두 삭제하고 본인인증 기록은 유지한다")
    void deleteChildrenOnSuspension() throws Exception {
        childProfileService.register(memberId, REQUEST);
        childProfileService.register(memberId, REQUEST);
        var other = childProfileService.register(otherMemberId, REQUEST).id();
        var now = LocalDateTime.now();

        send(new MemberSuspendedEvent(UUID.randomUUID(), now, new MemberSuspendedEvent.Data(memberId, "사유", now)));

        AsyncWait.until(TIMEOUT, () -> childrenOf(memberId).isEmpty());
        assertThat(childrenOf(otherMemberId)).extracting(ChildProfile::getId).containsExactly(other);
        assertThat(verifications.existsByMemberId(memberId)).isTrue();
        assertThat(verifications.existsByMemberId(otherMemberId)).isTrue();
    }

    @Test
    @DisplayName("같은 탈퇴 이벤트를 두 번 받아도 결과는 한 번 받은 것과 같고 DLT로 보내지 않는다")
    void duplicateEvent() throws Exception {
        childProfileService.register(memberId, REQUEST);
        var other = childProfileService.register(otherMemberId, REQUEST).id();
        var event = memberWithdrawn();

        send(event);
        var second = send(event);

        messages.awaitProcessed(GROUP, TOPIC, second);
        assertThat(childrenOf(memberId)).isEmpty();
        assertThat(childrenOf(otherMemberId)).extracting(ChildProfile::getId).containsExactly(other);
        assertThat(deadLetters()).isEmpty();
    }

    @Test
    @DisplayName("자녀 프로필이 받지 않는 종류는 건너뛰고 DLT로 보내지 않으며, 뒤 메시지를 처리한다")
    void skipOtherEventType() throws Exception {
        childProfileService.register(memberId, REQUEST);

        sendRaw("member.changed", UUID.randomUUID(), "받지 않는 종류라 읽지 않는 본문");

        // 키(회원 ID)가 같아 같은 파티션이므로, 뒤 메시지를 처리했으면 앞 메시지는 이미 처리됐다
        send(memberWithdrawn());

        AsyncWait.until(TIMEOUT, () -> childrenOf(memberId).isEmpty());
        assertThat(deadLetters()).isEmpty();
    }

    @Test
    @DisplayName("받는 종류인데 본문을 읽지 못하면 재시도 없이 DLT로 보내고, 로그에 본문을 남기지 않는다")
    void deadLetterUnreadableBody(CapturedOutput output) throws Exception {
        var marker = UUID.randomUUID().toString();
        var body = "{\"correlationId\":\"" + marker + "\",\"data\":{\"memberId\":\"숫자 아님\"}}";

        var sent = sendRaw("member.withdrawn", UUID.randomUUID(), body);

        assertDeadLetteredWithoutRetry(sent, body);
        assertThat(output.getOut()).contains("key=" + memberId).doesNotContain(marker);
    }

    private void assertDeadLetteredWithoutRetry(RecordMetadata sent, String body) {
        AsyncWait.until(TIMEOUT, () -> !deadLetters().isEmpty());
        assertThat(deadLetters()).singleElement().satisfies(deadLetter -> {
            assertThat(deadLetter.key()).isEqualTo(String.valueOf(memberId));
            assertThat(deadLetter.value()).isEqualTo(body);
            assertThat(deadLetter.timestamp() - sent.timestamp()).isLessThan(RETRY_BACKOFF_TOTAL_MS);
        });
    }

    private List<ChildProfile> childrenOf(Long memberId) {
        return childProfileRepository.findAllByMemberId(memberId);
    }

    private RecordMetadata send(DomainEvent event) throws Exception {
        return messages.send(TOPIC, event);
    }

    private MemberWithdrawnEvent memberWithdrawn() {
        return new MemberWithdrawnEvent(UUID.randomUUID(), LocalDateTime.now(),
            new MemberWithdrawnEvent.Data(memberId));
    }

    private RecordMetadata sendRaw(String eventType, UUID eventId, String body) throws Exception {
        return messages.sendRaw(TOPIC, String.valueOf(memberId), eventType, eventId, body);
    }

    private List<ConsumerRecord<String, String>> deadLetters() {
        return messages.deadLetters(DLT, String.valueOf(memberId));
    }
}
