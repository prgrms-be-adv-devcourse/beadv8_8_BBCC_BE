package com.bbcc.kidly.boundedcontext.member;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.member.app.MemberDeadLetterService;
import com.bbcc.kidly.boundedcontext.member.app.dto.MemberDeadLetterMessage;
import com.bbcc.kidly.boundedcontext.member.domain.MemberRepository;
import com.bbcc.kidly.boundedcontext.member.domain.ShippingAddressRepository;
import com.bbcc.kidly.member.support.security.TestAdmins;
import com.bbcc.kidly.shared.member.event.MemberWithdrawnEvent;
import com.bbcc.kidly.shared.seller.domain.SellerStatus;
import com.bbcc.kidly.shared.seller.event.SellerChangedEvent;
import com.bbcc.kidly.support.FunctionalTest;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;
import tools.jackson.databind.json.JsonMapper;

@FunctionalTest
class AdminMemberDeadLetterApiTest {

    private static final String PATH = "/api/v1/admin/member-dead-letters";
    private static final String SELLER_TOPIC = "kidly.seller.events";
    private static final String MEMBER_TOPIC = "kidly.member.events";
    private static final Long ADMIN_ID = 1L;

    @Autowired
    private RestTestClient client;

    @Autowired
    private MemberDeadLetterService service;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ShippingAddressRepository shippingAddressRepository;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("기본 OPEN 목록과 상세는 원본 본문을 응답하지 않는다")
    void listsWithoutPayload() {
        var id = receive(SELLER_TOPIC, 1L, "seller.changed", "private-body");

        client.get().uri(PATH).headers(TestAdmins.of(ADMIN_ID)).exchange().expectStatus().isOk().expectBody()
            .jsonPath("$.content[0].deadLetterId").isEqualTo(id.toString())
            .jsonPath("$.content[0].status").isEqualTo("OPEN")
            .jsonPath("$.content[0].sourceTopic").isEqualTo(SELLER_TOPIC)
            .jsonPath("$.content[0].eventType").isEqualTo("seller.changed")
            .jsonPath("$.content[0].payload").doesNotExist()
            .jsonPath("$.totalElements").isEqualTo(1);
        client.get().uri(PATH + "?status=REPROCESSED").headers(TestAdmins.of(ADMIN_ID)).exchange()
            .expectStatus().isOk().expectBody().jsonPath("$.content").isEmpty();
        client.get().uri(PATH + "/" + id).headers(TestAdmins.of(ADMIN_ID)).exchange().expectStatus().isOk()
            .expectBody()
            .jsonPath("$.deadLetterId").isEqualTo(id.toString())
            .jsonPath("$.payload").doesNotExist();
    }

    @Test
    @DisplayName("판매자 이벤트를 재처리하면 회원의 판매자 상태가 바뀌고 REPROCESSED가 된다")
    void reprocessesSellerEvent() {
        var memberId = memberRepository.save(MemberFixture.kakaoMember()).getId();
        var id = receive(SELLER_TOPIC, 1L, "seller.changed", sellerChanged(memberId));

        client.post().uri(PATH + "/" + id + "/reprocess").headers(TestAdmins.of(ADMIN_ID))
            .exchange().expectStatus().isOk().expectBody()
            .jsonPath("$.status").isEqualTo("REPROCESSED")
            .jsonPath("$.replayCount").isEqualTo(1)
            .jsonPath("$.lastReplayedBy").isEqualTo(ADMIN_ID.toString())
            .jsonPath("$.lastReplayFailureType").doesNotExist()
            .jsonPath("$.payload").doesNotExist();
        assertThat(memberRepository.findById(memberId).orElseThrow().sellerStatus()).isEqualTo(SellerStatus.ACTIVE);
    }

    @Test
    @DisplayName("회원 탈퇴 이벤트를 재처리하면 그 회원의 배송지를 지우고 REPROCESSED가 되며, 다시 불러도 재실행하지 않는다")
    void reprocessesMemberEvent() {
        var memberId = 7L;
        shippingAddressRepository.save(ShippingAddressFixture.address(memberId));
        var id = receive(MEMBER_TOPIC, 2L, "member.withdrawn", memberWithdrawn(memberId));

        client.post().uri(PATH + "/" + id + "/reprocess").headers(TestAdmins.of(ADMIN_ID))
            .exchange().expectStatus().isOk().expectBody()
            .jsonPath("$.status").isEqualTo("REPROCESSED")
            .jsonPath("$.replayCount").isEqualTo(1);
        assertThat(shippingAddressRepository.findAllByMemberIdOrderByIdAsc(memberId)).isEmpty();

        shippingAddressRepository.save(ShippingAddressFixture.address(memberId));
        client.post().uri(PATH + "/" + id + "/reprocess").headers(TestAdmins.of(ADMIN_ID))
            .exchange().expectStatus().isOk().expectBody()
            .jsonPath("$.status").isEqualTo("REPROCESSED")
            .jsonPath("$.replayCount").isEqualTo(1);
        assertThat(shippingAddressRepository.findAllByMemberIdOrderByIdAsc(memberId)).isNotEmpty();
    }

    @Test
    @DisplayName("처리기가 실패하면 200/OPEN으로 실패 이력을 돌려준다")
    void reportsReplayFailure() {
        var id = receive(SELLER_TOPIC, 1L, "seller.changed", "{\"data\":{\"sellerId\":\"숫자 아님\"}}");

        client.post().uri(PATH + "/" + id + "/reprocess").headers(TestAdmins.of(ADMIN_ID))
            .exchange().expectStatus().isOk().expectBody()
            .jsonPath("$.status").isEqualTo("OPEN")
            .jsonPath("$.replayCount").isEqualTo(1)
            .jsonPath("$.lastReplayedBy").isEqualTo(ADMIN_ID.toString())
            .jsonPath("$.lastReplayFailureType").exists()
            .jsonPath("$.payload").doesNotExist();
    }

    @Test
    @DisplayName("인증 · 없는 기록 · 상태 · 페이지 범위를 검증한다")
    void validatesRequests() {
        var id = receive(SELLER_TOPIC, 1L, "seller.changed", "private-body");

        client.post().uri(PATH + "/" + id + "/reprocess").exchange().expectStatus().isUnauthorized();
        client.get().uri(PATH + "/9223372036854775807").headers(TestAdmins.of(ADMIN_ID)).exchange()
            .expectStatus().isNotFound();
        client.post().uri(PATH + "/9223372036854775807/reprocess").headers(TestAdmins.of(ADMIN_ID)).exchange()
            .expectStatus().isNotFound();
        client.get().uri(PATH + "?status=UNKNOWN").headers(TestAdmins.of(ADMIN_ID)).exchange()
            .expectStatus().isBadRequest();
        client.get().uri(PATH + "?size=101").headers(TestAdmins.of(ADMIN_ID)).exchange()
            .expectStatus().isBadRequest();
        client.get().uri(PATH + "?page=-1").headers(TestAdmins.of(ADMIN_ID)).exchange()
            .expectStatus().isBadRequest();
        client.get().uri(PATH + "?page=2147483647").headers(TestAdmins.of(ADMIN_ID)).exchange()
            .expectStatus().isOk().expectBody().jsonPath("$.content").isEmpty();
    }

    private Long receive(String sourceTopic, long dltOffset, String eventType, String payload) {
        var eventId = UUID.randomUUID().toString();
        service.receive(new MemberDeadLetterMessage(sourceTopic + ".member.dlt", 0, dltOffset, sourceTopic, 0, 1L, "1",
            eventType, eventId, payload, "tools.jackson.core.JacksonException"));
        return jdbc.queryForObject("select id from member.dead_letter where event_id = ?", Long.class, eventId);
    }

    private String sellerChanged(Long sellerId) {
        return jsonMapper.writeValueAsString(new SellerChangedEvent(UUID.randomUUID(), MemberFixture.NOW,
            new SellerChangedEvent.Data(sellerId, "하은상회", SellerStatus.ACTIVE)));
    }

    private String memberWithdrawn(Long memberId) {
        return jsonMapper.writeValueAsString(new MemberWithdrawnEvent(UUID.randomUUID(), MemberFixture.NOW,
            new MemberWithdrawnEvent.Data(memberId)));
    }
}
