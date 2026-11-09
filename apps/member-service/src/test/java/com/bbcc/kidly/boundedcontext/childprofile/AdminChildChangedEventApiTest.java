package com.bbcc.kidly.boundedcontext.childprofile;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.childprofile.domain.ChildProfileRepository;
import com.bbcc.kidly.member.support.security.TestAdmins;
import com.bbcc.kidly.shared.childprofile.event.ChildChangedEvent;
import com.bbcc.kidly.support.CommittedEventRecorder;
import com.bbcc.kidly.support.FunctionalTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * 관리자가 기존 자녀의 child.changed를 다시 보내는 API를 HTTP 요청부터 응답까지 확인한다.
 * 나가는 이벤트는 커밋 뒤 이벤트를 모으는 빈으로 세고, 아웃박스에 쌓인 행도 확인한다.
 */
@FunctionalTest
class AdminChildChangedEventApiTest {

    private static final String PATH = "/api/v1/admin/children/changed-events";
    private static final Long ADMIN_ID = 100L;

    @Autowired
    private RestTestClient client;

    @Autowired
    private ChildProfileRepository childProfileRepository;

    @Autowired
    private CommittedEventRecorder eventRecorder;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("삭제되지 않은 자녀만 child.changed가 나가 아웃박스에 자녀 ID 키로 쌓이고, 삭제된 자녀는 빠진다")
    void republishLiveChildren() {
        var live = childProfileRepository.save(ChildProfileFixture.child(1L));
        var deleted = ChildProfileFixture.child(2L);
        deleted.delete(ChildProfileFixture.NOW);
        var deletedId = childProfileRepository.save(deleted).getId();
        eventRecorder.clear();

        var response = client.post().uri(PATH)
            .headers(TestAdmins.of(ADMIN_ID))
            .exchange()
            .expectStatus().isOk()
            .expectBody(PublishedCount.class)
            .returnResult()
            .getResponseBody();

        assertThat(response.publishedCount()).isEqualTo(1);
        assertThat(eventRecorder.events(ChildChangedEvent.class)).singleElement().satisfies(event -> {
            assertThat(event.data().childId()).isEqualTo(live.getId()).isNotEqualTo(deletedId);
            assertThat(event.data().memberId()).isEqualTo(1L);
            assertThat(event.data().deleted()).isFalse();
        });
        assertThat(jdbc.queryForList(
            "select message_key from childprofile.child_profile_outbox where event_type = 'child.changed'",
            String.class)).containsExactly(String.valueOf(live.getId()));
    }

    @Test
    @DisplayName("관리자 헤더가 없으면 401이고 아무것도 보내지 않는다")
    void requiresAdmin() {
        childProfileRepository.save(ChildProfileFixture.child(1L));
        eventRecorder.clear();

        client.post().uri(PATH).exchange().expectStatus().isUnauthorized();

        assertThat(eventRecorder.events(ChildChangedEvent.class)).isEmpty();
    }

    private record PublishedCount(int publishedCount) {
    }
}
