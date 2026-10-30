package com.bbcc.kidly.boundedcontext.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.member.app.MemberService;
import com.bbcc.kidly.boundedcontext.member.domain.MemberRepository;
import com.bbcc.kidly.boundedcontext.member.domain.outbox.MemberOutboxMessage;
import com.bbcc.kidly.boundedcontext.member.domain.outbox.MemberOutboxRepository;
import com.bbcc.kidly.global.eventpublisher.EventPublisher;
import com.bbcc.kidly.shared.member.event.MemberWithdrawnEvent;
import com.bbcc.kidly.support.IntegrationTest;
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

/**
 * 회원 탈퇴 이벤트를 커밋 직전에 아웃박스에 저장하는지 실제 트랜잭션으로 확인한다.
 * 저장은 발행한 트랜잭션 안에서 일어나야 하므로 테스트에 @Transactional을 붙이지 않는다.
 */
@IntegrationTest
class MemberOutboxRecorderIntegrationTest {

    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 10, 30, 12, 0);

    @Autowired
    private MemberService memberService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private MemberOutboxRepository outboxRepository;

    @Autowired
    private EventPublisher eventPublisher;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("탈퇴하면 그 트랜잭션에서 회원 ID 키의 member.withdrawn 행이 하나 남는다")
    void recordOnWithdraw() {
        var memberId = memberRepository.save(MemberFixture.kakaoMember()).getId();

        memberService.withdraw(memberId);

        assertThat(outboxRows()).singleElement().satisfies(row -> assertThat(row)
            .containsEntry("event_type", "member.withdrawn")
            .containsEntry("topic", "kidly.member.events")
            .containsEntry("message_key", String.valueOf(memberId)));
    }

    @Test
    @DisplayName("탈퇴 이벤트를 발행한 트랜잭션이 롤백되면 아웃박스 행이 남지 않는다")
    void recordNothingOnRollback() {
        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publish(withdrawn(UUID.randomUUID()));
            status.setRollbackOnly();
        });

        assertThat(outboxRows()).isEmpty();
    }

    @Test
    @DisplayName("아웃박스 저장이 실패하면 같은 트랜잭션의 업무도 커밋되지 않는다")
    void rollBackBusinessWhenRecordFails() {
        // 같은 eventId 행을 먼저 넣어 두면 커밋 직전 INSERT가 event_id UNIQUE에 걸린다
        var eventId = UUID.randomUUID();
        outboxRepository.save(MemberOutboxMessage.create(MemberOutboxMessageFixture.withdrawn(eventId)));

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            memberRepository.save(MemberFixture.kakaoMember());
            eventPublisher.publish(withdrawn(eventId));
        })).isInstanceOf(DataIntegrityViolationException.class);

        assertThat(jdbcTemplate.queryForObject("select count(*) from member.member", Long.class)).isZero();
        assertThat(outboxRows()).hasSize(1);
    }

    private static MemberWithdrawnEvent withdrawn(UUID eventId) {
        return new MemberWithdrawnEvent(eventId, OCCURRED_AT, new MemberWithdrawnEvent.Data(1L));
    }

    private List<Map<String, Object>> outboxRows() {
        return jdbcTemplate.queryForList(
            "select event_id, event_type, topic, message_key from member.member_outbox order by id");
    }
}
