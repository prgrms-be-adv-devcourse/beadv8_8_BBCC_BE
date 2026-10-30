package com.bbcc.kidly.boundedcontext.childprofile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.childprofile.app.ChildProfileService;
import com.bbcc.kidly.boundedcontext.childprofile.app.dto.ChildProfileRequest;
import com.bbcc.kidly.boundedcontext.childprofile.domain.ChildProfileRepository;
import com.bbcc.kidly.global.eventpublisher.EventPublisher;
import com.bbcc.kidly.shared.childprofile.domain.BodyType;
import com.bbcc.kidly.shared.childprofile.domain.Gender;
import com.bbcc.kidly.shared.childprofile.domain.PreferredFit;
import com.bbcc.kidly.shared.childprofile.event.ChildChangedEvent;
import com.bbcc.kidly.shared.event.DomainEvent;
import com.bbcc.kidly.shared.member.event.MemberWithdrawnEvent;
import com.bbcc.kidly.support.IntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * 자녀 프로필이 발행한 이벤트를 커밋 직전에 아웃박스에 저장하는지 실제 트랜잭션으로 확인한다.
 * 저장은 발행한 트랜잭션 안에서 일어나야 하므로 테스트에 @Transactional을 붙이지 않고 발행만 트랜잭션 안에서 한다.
 */
@IntegrationTest
class ChildProfileOutboxRecorderIntegrationTest {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();
    private static final Long MEMBER_ID = 1L;
    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 10, 30, 12, 0);
    private static final ChildProfileRequest REQUEST = new ChildProfileRequest(
        "첫째", LocalDate.of(2019, 1, 15), Gender.MALE, new BigDecimal("115.0"), new BigDecimal("20.0"),
        BodyType.NORMAL, PreferredFit.LOOSE);

    @Autowired
    private ChildProfileService childProfileService;

    @Autowired
    private ChildProfileRepository childProfileRepository;

    @Autowired
    private EventPublisher eventPublisher;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("자녀를 등록하면 그 트랜잭션에서 자녀 ID 키의 child.changed 행이 하나 남는다")
    void recordOnRegister() {
        var childId = childProfileService.register(MEMBER_ID, REQUEST).id();

        assertThat(outboxRows()).singleElement().satisfies(row -> assertThat(row)
            .containsEntry("event_type", "child.changed")
            .containsEntry("topic", "kidly.childprofile.events")
            .containsEntry("message_key", String.valueOf(childId)));
    }

    @Test
    @DisplayName("회원 탈퇴를 받아 자녀를 지운 새 트랜잭션에서도 삭제 child.changed가 저장된다")
    void recordDeletedOnMemberWithdrawn() {
        var childId = childProfileService.register(MEMBER_ID, REQUEST).id();

        // 탈퇴 리스너는 발행한 트랜잭션이 커밋된 뒤 새 트랜잭션에서 자녀를 지우고 child.changed를 다시 발행한다
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(new MemberWithdrawnEvent(
            UUID.randomUUID(), OCCURRED_AT, new MemberWithdrawnEvent.Data(MEMBER_ID))));

        var rows = outboxRows();
        assertThat(rows).hasSize(2);
        assertThat(rows.get(1)).containsEntry("message_key", String.valueOf(childId));
        assertThat(JSON_MAPPER.readTree((String) rows.get(1).get("payload")).get("data").get("deleted").asBoolean())
            .isTrue();
    }

    @Test
    @DisplayName("자녀 이벤트를 발행한 트랜잭션이 롤백되면 아웃박스 행이 남지 않는다")
    void recordNothingOnRollback() {
        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publish(childChanged());
            status.setRollbackOnly();
        });

        assertThat(outboxRows()).isEmpty();
    }

    @Test
    @DisplayName("같은 이벤트를 한 트랜잭션에서 두 번 발행해도 아웃박스 행은 하나만 남는다")
    void recordSameEventOnce() {
        var event = childChanged();

        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publish(event);
            eventPublisher.publish(event);
        });

        assertThat(outboxRows()).singleElement().satisfies(row -> assertThat(row)
            .containsEntry("event_id", event.eventId()));
    }

    @Test
    @DisplayName("다른 도메인이 발행한 이벤트는 커밋돼도 저장하지 않는다(자녀가 없는 회원의 탈퇴)")
    void ignoreOtherDomainEvent() {
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(new MemberWithdrawnEvent(
            UUID.randomUUID(), OCCURRED_AT, new MemberWithdrawnEvent.Data(MEMBER_ID))));

        assertThat(outboxRows()).isEmpty();
    }

    @Test
    @DisplayName("아웃박스 저장이 실패하면 같은 트랜잭션의 업무도 커밋되지 않는다")
    void rollBackBusinessWhenRecordFails() {
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            childProfileRepository.save(ChildProfileFixture.child(MEMBER_ID));
            eventPublisher.publish(new TooLongTypeEvent(UUID.randomUUID(), OCCURRED_AT, 3L));
        })).isInstanceOf(DataIntegrityViolationException.class);

        assertThat(jdbcTemplate.queryForObject("select count(*) from childprofile.child_profile", Long.class))
            .isZero();
        assertThat(outboxRows()).isEmpty();
    }

    // 자녀 3의 수정으로 나간 자녀 정보 변경됨이다. 아웃박스는 이벤트 값을 그대로 옮기므로 실제 자녀가 없어도 된다
    private static ChildChangedEvent childChanged() {
        return new ChildChangedEvent(UUID.randomUUID(), OCCURRED_AT, new ChildChangedEvent.Data(
            3L, MEMBER_ID, new BigDecimal("116.0"), new BigDecimal("21.0"), Gender.MALE,
            LocalDate.of(2019, 1, 15), BodyType.NORMAL, PreferredFit.LOOSE, false));
    }

    // 아웃박스 저장을 실패시키려고 만든 자녀 프로필 이벤트다. 종류가 event_type 칸 길이(60자)보다 길어 INSERT가 실패한다
    private record TooLongTypeEvent(UUID eventId, LocalDateTime occurredAt, Long data) implements DomainEvent {

        @Override
        public String aggregateId() {
            return String.valueOf(data);
        }

        @Override
        public String eventType() {
            return "child." + "x".repeat(60);
        }

        @Override
        public String producer() {
            return "childprofile";
        }
    }

    private List<Map<String, Object>> outboxRows() {
        return jdbcTemplate.queryForList(
            "select event_id, event_type, topic, message_key, payload "
                + "from childprofile.child_profile_outbox order by id");
    }
}
