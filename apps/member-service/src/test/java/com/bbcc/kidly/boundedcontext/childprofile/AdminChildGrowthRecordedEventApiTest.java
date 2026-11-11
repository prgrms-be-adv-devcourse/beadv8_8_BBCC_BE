package com.bbcc.kidly.boundedcontext.childprofile;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.childprofile.domain.ChildProfileRepository;
import com.bbcc.kidly.member.support.security.TestAdmins;
import com.bbcc.kidly.shared.childprofile.domain.BodyType;
import com.bbcc.kidly.shared.childprofile.domain.PreferredFit;
import com.bbcc.kidly.shared.childprofile.event.ChildGrowthRecordedEvent;
import com.bbcc.kidly.support.CommittedEventRecorder;
import com.bbcc.kidly.support.FunctionalTest;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;

@FunctionalTest
class AdminChildGrowthRecordedEventApiTest {

    private static final String PATH = "/api/v1/admin/children/growth-recorded-events";
    private static final Long ADMIN_ID = 100L;

    @Autowired
    private RestTestClient client;

    @Autowired
    private ChildProfileRepository repository;

    @Autowired
    private CommittedEventRecorder recorder;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("삭제되지 않은 자녀의 성장 이력만 원래 ID · 기록 시각에 새 eventId로 나가고, 다시 불러도 같은 이력이 나간다")
    void republishesLiveRecords() {
        var child = ChildProfileFixture.child(1L);
        child.update(ChildProfileFixture.updateInfo("첫째", new BigDecimal("118.0"), ChildProfileFixture.WEIGHT,
            BodyType.NORMAL, PreferredFit.LOOSE), ChildProfileFixture.NOW.plusDays(30));
        child = repository.save(child);
        var deleted = ChildProfileFixture.child(2L);
        deleted.delete(ChildProfileFixture.NOW);
        repository.save(deleted);
        var ids = jdbc.queryForList("select id from childprofile.growth_record where child_profile_id = ? order by id",
            Long.class, child.getId());
        recorder.clear();

        for (int i = 0; i < 2; i++) {
            var response = client.post().uri(PATH).headers(TestAdmins.of(ADMIN_ID)).exchange().expectStatus().isOk()
                .expectBody(PublishedCount.class).returnResult().getResponseBody();
            assertThat(response.publishedCount()).isEqualTo(2);
        }
        var events = recorder.events(ChildGrowthRecordedEvent.class);
        assertThat(events).hasSize(4);
        assertThat(events).extracting(event -> event.data().growthRecordId())
            .containsExactly(ids.get(0), ids.get(1), ids.get(0), ids.get(1));
        assertThat(events).allSatisfy(event -> assertThat(event.occurredAt()).isEqualTo(event.data().recordedAt()));
        assertThat(events.get(0).occurredAt()).isEqualTo(ChildProfileFixture.NOW);
        assertThat(events.get(1).occurredAt()).isEqualTo(ChildProfileFixture.NOW.plusDays(30));
        assertThat(events.get(0).eventId()).isNotEqualTo(events.get(2).eventId());
        assertThat(events.get(1).eventId()).isNotEqualTo(events.get(3).eventId());
        assertThat(jdbc.queryForObject("select count(*) from childprofile.child_profile_outbox "
            + "where event_type = 'child.growth-recorded'", Long.class)).isEqualTo(4);
    }

    @Test
    @DisplayName("관리자 헤더가 없으면 401이고 아무것도 보내지 않는다")
    void requiresAdmin() {
        repository.save(ChildProfileFixture.child(1L));
        recorder.clear();
        client.post().uri(PATH).exchange().expectStatus().isUnauthorized();
        assertThat(recorder.events(ChildGrowthRecordedEvent.class)).isEmpty();
    }

    private record PublishedCount(int publishedCount) {
    }
}
