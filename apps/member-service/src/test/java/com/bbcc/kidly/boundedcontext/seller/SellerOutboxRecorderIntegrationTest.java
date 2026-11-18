package com.bbcc.kidly.boundedcontext.seller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.seller.domain.SellerApplicationRepository;
import com.bbcc.kidly.boundedcontext.seller.domain.outbox.SellerOutboxMessage;
import com.bbcc.kidly.boundedcontext.seller.domain.outbox.SellerOutboxRepository;
import com.bbcc.kidly.shared.member.event.MemberChangedEvent;
import com.bbcc.kidly.shared.seller.domain.SellerStatus;
import com.bbcc.kidly.shared.seller.event.SellerApplicationRejectedEvent;
import com.bbcc.kidly.shared.seller.event.SellerChangedEvent;
import com.bbcc.kidly.support.IntegrationTest;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
class SellerOutboxRecorderIntegrationTest {

    @Autowired
    private ApplicationEventPublisher publisher;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SellerApplicationRepository applicationRepository;

    @Autowired
    private SellerOutboxRepository outboxRepository;

    @Test
    @DisplayName("판매자 이벤트만 업무 트랜잭션에 저장한다")
    void recordsChanged() {
        var now = LocalDateTime.now();
        transactionTemplate.executeWithoutResult(status -> {
            publisher.publishEvent(new MemberChangedEvent(UUID.randomUUID(), now,
                new MemberChangedEvent.Data(1L, "회원")));
            publisher.publishEvent(new SellerChangedEvent(UUID.randomUUID(), now,
                new SellerChangedEvent.Data(1L, "상호", SellerStatus.ACTIVE)));
            publisher.publishEvent(new SellerApplicationRejectedEvent(UUID.randomUUID(), now,
                new SellerApplicationRejectedEvent.Data(1L, 1L, "사유", now)));
        });
        assertThat(jdbcTemplate.queryForList("select event_type from seller.seller_outbox", String.class))
            .containsExactlyInAnyOrder("seller.changed", "seller.application-rejected");
    }

    @Test
    @DisplayName("업무 트랜잭션이 롤백되면 메시지도 남지 않는다")
    void rollsBack() {
        transactionTemplate.executeWithoutResult(status -> {
            publisher.publishEvent(new SellerChangedEvent(UUID.randomUUID(), LocalDateTime.now(),
                new SellerChangedEvent.Data(1L, "상호", SellerStatus.ACTIVE)));
            status.setRollbackOnly();
        });
        assertThat(jdbcTemplate.queryForObject("select count(*) from seller.seller_outbox", Long.class)).isZero();
    }

    @Test
    @DisplayName("아웃박스 저장 실패는 업무 저장도 롤백한다")
    void rollsBackBusinessWhenOutboxFails() {
        var eventId = UUID.randomUUID();
        outboxRepository.save(new SellerOutboxMessage(SellerOutboxMessageFixture.changed(eventId)));
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            applicationRepository.save(SellerFixture.application(1L));
            publisher.publishEvent(new SellerChangedEvent(eventId, LocalDateTime.now(),
                new SellerChangedEvent.Data(1L, "상호", SellerStatus.ACTIVE)));
        })).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbcTemplate.queryForObject("select count(*) from seller.seller_application", Long.class)).isZero();
    }
}
