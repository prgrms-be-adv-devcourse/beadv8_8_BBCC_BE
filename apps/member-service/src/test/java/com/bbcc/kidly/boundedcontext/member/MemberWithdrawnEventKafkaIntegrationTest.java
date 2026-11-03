package com.bbcc.kidly.boundedcontext.member;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.member.app.ShippingAddressService;
import com.bbcc.kidly.boundedcontext.member.app.dto.ShippingAddressRequest;
import com.bbcc.kidly.boundedcontext.member.domain.DefaultShippingAddressRepository;
import com.bbcc.kidly.boundedcontext.member.domain.ShippingAddress;
import com.bbcc.kidly.boundedcontext.member.domain.ShippingAddressRepository;
import com.bbcc.kidly.shared.member.event.MemberWithdrawnEvent;
import com.bbcc.kidly.support.AsyncWait;
import com.bbcc.kidly.support.KafkaIntegrationTest;
import com.bbcc.kidly.support.KafkaMessages;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
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
 * member.withdrawn을 kidly.member.events에서 받아 그 회원의 배송지를 모두 지운다.
 * 리스너는 이 테스트 안에서만 켜고 끈다. 같은 토픽에는 다른 테스트의 회원 이벤트도 쌓여 이어서 받을 수 있으므로
 * 회원 ID를 테스트마다 무작위로 정하고, 결과는 이 테스트의 회원 ID · 키로 걸러 본다.
 */
@KafkaIntegrationTest
class MemberWithdrawnEventKafkaIntegrationTest {

    private static final String TOPIC = "kidly.member.events";
    private static final String DLT = "kidly.member.events.member.dlt";
    private static final String GROUP = "kidly.member";
    private static final String LISTENER_ID = "memberMemberEventListener";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    // 재시도는 1초 간격 3번이라, 재시도했다면 DLT에 들어가기까지 3초 이상 걸린다
    private static final long RETRY_BACKOFF_TOTAL_MS = 3_000;

    @Autowired
    private ShippingAddressRepository shippingAddressRepository;

    @Autowired
    private ShippingAddressService shippingAddressService;

    @Autowired
    private DefaultShippingAddressRepository defaultShippingAddressRepository;

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
    @DisplayName("탈퇴 이벤트를 Kafka로 받아 그 회원의 배송지 · 기본 배송지를 모두 지운다")
    void deleteAddressesOnWithdraw() throws Exception {
        // 기본 배송지 행이 배송지를 참조하므로(FK) 기본 배송지가 있어야 지우는 순서까지 확인된다
        shippingAddressService.register(memberId, defaultAddressRequest());
        register(memberId);
        var other = register(otherMemberId).getId();

        messages.send(TOPIC, memberWithdrawn());

        AsyncWait.until(TIMEOUT, () -> addressesOf(memberId).isEmpty());
        assertThat(defaultShippingAddressRepository.findById(memberId)).isEmpty();
        assertThat(addressesOf(otherMemberId))
            .extracting(ShippingAddress::getId).containsExactly(other);
    }

    @Test
    @DisplayName("같은 탈퇴 이벤트를 두 번 받아도 결과는 한 번 받은 것과 같고 DLT로 보내지 않는다")
    void duplicateEvent() throws Exception {
        register(memberId);
        var other = register(otherMemberId).getId();
        var event = memberWithdrawn();

        // 회원 릴레이가 보낸 뒤 보낸 기록을 남기기 전에 멈추면 같은 메시지를 한 번 더 보낸다
        messages.send(TOPIC, event);
        var second = messages.send(TOPIC, event);

        // 두 번째에는 지울 배송지가 없어 결과가 바뀌지 않으므로, 그룹이 두 번째 메시지까지 처리 완료를 표시할 때까지 기다린다
        messages.awaitProcessed(GROUP, TOPIC, second);
        assertThat(addressesOf(memberId)).isEmpty();
        assertThat(addressesOf(otherMemberId))
            .extracting(ShippingAddress::getId).containsExactly(other);
        assertThat(messages.deadLetters(DLT, String.valueOf(memberId))).isEmpty();
    }

    @Test
    @DisplayName("받는 종류인데 본문을 읽지 못하면 재시도 없이 회원 DLT로 보낸다")
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

    private ShippingAddress register(Long memberId) {
        return shippingAddressRepository.save(ShippingAddressFixture.address(memberId));
    }

    private static ShippingAddressRequest defaultAddressRequest() {
        var info = ShippingAddressFixture.info();
        return new ShippingAddressRequest(info.recipientName(), info.recipientPhone(), info.zipCode(),
            info.roadAddress(), info.detailAddress(), true);
    }

    private List<ShippingAddress> addressesOf(Long memberId) {
        return shippingAddressRepository.findAllByMemberIdOrderByIdAsc(memberId);
    }

    private MemberWithdrawnEvent memberWithdrawn() {
        return new MemberWithdrawnEvent(UUID.randomUUID(), LocalDateTime.now(),
            new MemberWithdrawnEvent.Data(memberId));
    }
}
