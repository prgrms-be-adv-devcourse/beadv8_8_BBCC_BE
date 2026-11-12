package com.bbcc.kidly.boundedcontext.member;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.member.in.dto.DevMemberSeedResponse;
import com.bbcc.kidly.support.FunctionalTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;

@FunctionalTest
class DevMemberApiTest {

    private static final String PATH = "/api/v1/dev/members";

    @Autowired
    private RestTestClient client;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("더미 회원을 만들면 이메일 없는 회원이 생기고 created가 true다")
    void createsMemberWithoutEmail() {
        var response = post("buyer-1", "더미구매자1").expectStatus().isOk()
            .expectBody(DevMemberSeedResponse.class).returnResult().getResponseBody();

        assertThat(response.created()).isTrue();
        assertThat(jdbcTemplate.queryForMap("select nickname, email from member.member where id = ?",
            response.memberId())).containsEntry("nickname", "더미구매자1").containsEntry("email", null);
    }

    @Test
    @DisplayName("같은 key로 다시 부르면 같은 회원을 돌려주고 created가 false다")
    void sameKeyIsIdempotent() {
        var first = post("buyer-1", "더미구매자1").expectBody(DevMemberSeedResponse.class)
            .returnResult().getResponseBody();

        var second = post("buyer-1", "다른닉네임").expectStatus().isOk()
            .expectBody(DevMemberSeedResponse.class).returnResult().getResponseBody();

        assertThat(second.memberId()).isEqualTo(first.memberId());
        assertThat(second.created()).isFalse();
        assertThat(jdbcTemplate.queryForObject("select count(*) from member.member", Long.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("다른 key가 같은 닉네임이면 409다")
    void nicknameConflict() {
        post("buyer-1", "더미구매자1").expectStatus().isOk();

        post("buyer-2", "더미구매자1").expectStatus().isEqualTo(409);
    }

    @Test
    @DisplayName("key 형식이나 닉네임 길이가 틀리면 400이다")
    void rejectsInvalidInput() {
        post("bad key!", "더미구매자1").expectStatus().isBadRequest();
        post("buyer-1", "가").expectStatus().isBadRequest();
    }

    private RestTestClient.ResponseSpec post(String key, String nickname) {
        return client.post().uri(PATH).contentType(MediaType.APPLICATION_JSON)
            .body("{\"key\":\"" + key + "\",\"nickname\":\"" + nickname + "\"}").exchange();
    }
}
