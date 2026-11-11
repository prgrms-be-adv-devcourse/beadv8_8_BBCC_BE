package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.RecommendKafkaMessages.RETRY_BACKOFF_TOTAL_MS;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendKafkaMessages.TIMEOUT;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.app.replica.RecommendReplicaChildService;
import com.bbcc.kidly.boundedcontext.recommend.domain.activity.ActivityRecord;
import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.ReferenceGarment;
import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.ReferenceGarmentRepository;
import com.bbcc.kidly.boundedcontext.recommend.out.activity.JpaActivityRecordRepository;
import com.bbcc.kidly.shared.childprofile.domain.BodyType;
import com.bbcc.kidly.shared.childprofile.domain.Gender;
import com.bbcc.kidly.shared.childprofile.domain.PreferredFit;
import com.bbcc.kidly.shared.childprofile.event.ChildChangedEvent;
import com.bbcc.kidly.shared.member.event.MemberWithdrawnEvent;
import com.bbcc.kidly.support.AsyncWait;
import com.bbcc.kidly.support.KafkaIntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.json.JsonMapper;

/**
 * 같은 토픽에는 다른 테스트의 메시지도 쌓여 이어서 받을 수 있으므로, 회원 ID(이벤트의 키)를 테스트마다 새로 만든다.
 */
@KafkaIntegrationTest
class RecommendMemberKafkaIntegrationTest {

    private static final String MEMBER_TOPIC = "kidly.member.events";
    private static final String MEMBER_DLT = "kidly.member.events.recommend.dlt";
    private static final LocalDateTime SEARCHED_AT = LocalDateTime.of(2026, 11, 3, 10, 0);
    private static final LocalDateTime WITHDRAWN_AT = LocalDateTime.of(2026, 11, 4, 9, 0);

    @Autowired
    private JpaActivityRecordRepository activityRecordRepository;

    @Autowired
    private RecommendReplicaChildService replicaChildService;

    @Autowired
    private ReferenceGarmentRepository referenceGarmentRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

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

    // stop()은 처리 중인 메시지가 끝날 때까지 기다리므로 뒤이은 테이블 비우기와 겹치지 않는다
    @AfterEach
    void stopListeners() {
        listeners.forEach(MessageListenerContainer::stop);
    }

    @Test
    @DisplayName("회원 탈퇴를 회원 토픽에서 받아 그 회원의 활동 기록만 지운다")
    void deleteActivityRecordsOfWithdrawnMember() throws Exception {
        Long memberId = newId();
        Long otherMemberId = newId();
        activityRecordRepository.saveAndFlush(search(memberId));
        activityRecordRepository.saveAndFlush(search(memberId));
        activityRecordRepository.saveAndFlush(search(otherMemberId));

        messages.send(MEMBER_TOPIC, withdrawn(memberId));

        AsyncWait.until(TIMEOUT, () -> recordsOf(memberId).isEmpty());
        assertThat(recordsOf(otherMemberId)).hasSize(1);
    }

    @Test
    @DisplayName("회원 탈퇴를 회원 토픽에서 받아 삭제된 자녀를 포함한 그 회원 자녀의 기준 옷만 지운다")
    void deleteGarmentsOfWithdrawnMember() throws Exception {
        Long memberId = newId();
        Long childId = newId();
        Long deletedChildId = newId();
        Long otherMemberChildId = newId();
        replicaChildService.applyChildChanged(childChanged(childId, memberId, false));
        replicaChildService.applyChildChanged(childChanged(deletedChildId, memberId, true));
        replicaChildService.applyChildChanged(childChanged(otherMemberChildId, newId(), false));
        transactionTemplate.executeWithoutResult(status -> {
            referenceGarmentRepository.save(ReferenceGarmentFixture.top(childId));
            referenceGarmentRepository.save(ReferenceGarmentFixture.bottom(childId));
            referenceGarmentRepository.save(ReferenceGarmentFixture.top(deletedChildId));
            referenceGarmentRepository.save(ReferenceGarmentFixture.top(otherMemberChildId));
        });

        messages.send(MEMBER_TOPIC, withdrawn(memberId));

        AsyncWait.until(TIMEOUT, () -> garmentsOf(childId).isEmpty() && garmentsOf(deletedChildId).isEmpty());
        assertThat(garmentsOf(otherMemberChildId)).hasSize(1);
    }

    @Test
    @DisplayName("같은 회원 탈퇴를 두 번 받아도 DLT로 보내지 않는다")
    void acceptSameWithdrawnTwice() throws Exception {
        Long memberId = newId();
        var event = withdrawn(memberId);
        activityRecordRepository.saveAndFlush(search(memberId));

        messages.send(MEMBER_TOPIC, event);
        var second = messages.send(MEMBER_TOPIC, event);

        messages.awaitProcessed(MEMBER_TOPIC, second);
        assertThat(recordsOf(memberId)).isEmpty();
        assertThat(messages.deadLetters(MEMBER_DLT, String.valueOf(memberId))).isEmpty();
    }

    @Test
    @DisplayName("회원 토픽에서 추천이 받지 않는 종류는 건너뛰고 DLT로 보내지 않으며, 뒤 메시지를 처리한다")
    void skipUnknownMemberEvent() throws Exception {
        Long memberId = newId();
        String key = String.valueOf(memberId);
        activityRecordRepository.saveAndFlush(search(memberId));
        messages.sendRaw(MEMBER_TOPIC, key, "member.registered", UUID.randomUUID(), "받지 않는 종류라 읽지 않는 본문");

        // 키가 같아 같은 파티션이므로, 뒤 메시지를 처리했으면 앞 메시지는 이미 처리됐다
        messages.send(MEMBER_TOPIC, withdrawn(memberId));

        AsyncWait.until(TIMEOUT, () -> recordsOf(memberId).isEmpty());
        assertThat(messages.deadLetters(MEMBER_DLT, key)).isEmpty();
    }

    @Test
    @DisplayName("회원 메시지에 eventType 헤더가 없으면 재시도 없이 회원 DLT로 보낸다")
    void deadLetterWithoutEventType() throws Exception {
        String key = String.valueOf(newId());

        var sent = messages.sendRaw(MEMBER_TOPIC, key, null, UUID.randomUUID(), "어떤 이벤트인지 알 수 없는 본문");

        AsyncWait.until(TIMEOUT, () -> !messages.deadLetters(MEMBER_DLT, key).isEmpty());
        assertThat(messages.deadLetters(MEMBER_DLT, key)).singleElement()
            .satisfies(deadLetter -> assertThat(deadLetter.timestamp() - sent.timestamp())
                .isLessThan(RETRY_BACKOFF_TOTAL_MS));
    }

    @Test
    @DisplayName("회원 ID가 없는 탈퇴는 재시도 없이 회원 DLT로 보내고 다른 기록은 지우지 않는다")
    void deadLetterWithdrawnWithoutMember() throws Exception {
        Long otherMemberId = newId();
        activityRecordRepository.saveAndFlush(search(otherMemberId));
        var withoutMember = new MemberWithdrawnEvent(UUID.randomUUID(), WITHDRAWN_AT,
            new MemberWithdrawnEvent.Data(null));
        String key = String.valueOf(newId());

        var sent = messages.sendRaw(MEMBER_TOPIC, key, withoutMember.eventType(), withoutMember.eventId(),
            body(withoutMember));

        AsyncWait.until(TIMEOUT, () -> !messages.deadLetters(MEMBER_DLT, key).isEmpty());
        assertThat(messages.deadLetters(MEMBER_DLT, key)).singleElement()
            .satisfies(deadLetter -> assertThat(deadLetter.timestamp() - sent.timestamp())
                .isLessThan(RETRY_BACKOFF_TOTAL_MS));
        assertThat(recordsOf(otherMemberId)).hasSize(1);
    }

    // 회원 ID가 없는 탈퇴는 보내는 쪽이 만들지 않으므로 키를 따로 정해 본문만 같은 형식으로 만든다
    private String body(MemberWithdrawnEvent event) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("eventId", event.eventId());
        body.put("eventType", event.eventType());
        body.put("schemaVersion", event.schemaVersion());
        body.put("occurredAt", event.occurredAt());
        body.put("producer", event.producer());
        body.put("aggregateId", event.aggregateId());
        body.put("correlationId", event.correlationId());
        body.put("data", event.data());
        return jsonMapper.writeValueAsString(body);
    }

    private List<ActivityRecord> recordsOf(Long memberId) {
        return activityRecordRepository.findAll().stream()
            .filter(record -> memberId.equals(record.getMemberId()))
            .toList();
    }

    private List<ReferenceGarment> garmentsOf(Long childId) {
        return transactionTemplate.execute(status -> referenceGarmentRepository.findAllByChildProfileId(childId));
    }

    private static ChildChangedEvent childChanged(Long childId, Long memberId, boolean deleted) {
        return new ChildChangedEvent(UUID.randomUUID(), WITHDRAWN_AT.minusDays(1), new ChildChangedEvent.Data(childId,
            memberId, new BigDecimal("115.0"), new BigDecimal("20.0"), Gender.MALE, LocalDate.of(2020, 5, 1),
            BodyType.NORMAL, PreferredFit.LOOSE, deleted));
    }

    private static Long newId() {
        return ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
    }

    private static ActivityRecord search(Long memberId) {
        return ActivityRecord.searched(new ActivityRecord.SearchInfo(UUID.randomUUID(), memberId, "맨투맨", 52L,
            Map.of("COLOR", List.of(3L, 5L)), 12L, SEARCHED_AT, null));
    }

    private static MemberWithdrawnEvent withdrawn(Long memberId) {
        return new MemberWithdrawnEvent(UUID.randomUUID(), WITHDRAWN_AT, new MemberWithdrawnEvent.Data(memberId));
    }
}
