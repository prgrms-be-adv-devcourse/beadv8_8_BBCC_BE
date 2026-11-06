package com.bbcc.kidly.boundedcontext.member;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.member.domain.MemberRepository;
import com.bbcc.kidly.member.support.security.TestAdmins;
import com.bbcc.kidly.shared.member.event.MemberRegisteredEvent;
import com.bbcc.kidly.support.CommittedEventRecorder;
import com.bbcc.kidly.support.FunctionalTest;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * 관리자가 기존 회원의 가입 이벤트를 다시 보내는 API를 HTTP 요청부터 응답까지 확인한다.
 * 나가는 이벤트는 커밋 뒤 이벤트를 모으는 빈으로 센다.
 */
@FunctionalTest
class AdminMemberRegisteredEventApiTest {

    private static final String PATH = "/api/v1/admin/members/registered-events";
    private static final Long ADMIN_ID = 100L;

    @Autowired
    private RestTestClient client;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private CommittedEventRecorder eventRecorder;

    @Test
    @DisplayName("이메일이 있는 회원만 가입 이벤트가 resent=true로 나가고, 이메일 없는 회원 · 탈퇴 회원은 빠진다")
    void republishMembersWithEmail() {
        var withEmail = memberRepository.save(MemberFixture.kakaoMember("1"));
        var withoutEmail = memberRepository.save(MemberFixture.withoutEmail("2"));
        var withdrawn = MemberFixture.kakaoMember("3");
        withdrawn.withdraw(LocalDateTime.now());
        memberRepository.save(withdrawn);
        eventRecorder.clear();

        var response = client.post().uri(PATH)
            .headers(TestAdmins.of(ADMIN_ID))
            .exchange()
            .expectStatus().isOk()
            .expectBody(PublishedCount.class)
            .returnResult()
            .getResponseBody();

        var events = eventRecorder.events(MemberRegisteredEvent.class);
        assertThat(response.publishedCount()).isEqualTo(events.size());
        assertThat(events).extracting(event -> event.data().memberId())
            .contains(withEmail.getId())
            .doesNotContain(withoutEmail.getId(), withdrawn.getId());
        assertThat(events).filteredOn(event -> event.data().memberId().equals(withEmail.getId()))
            .singleElement()
            .satisfies(event -> {
                assertThat(event.data().email()).isEqualTo(withEmail.getEmail());
                assertThat(event.data().resent()).isTrue();
            });
    }

    private record PublishedCount(int publishedCount) {
    }
}
