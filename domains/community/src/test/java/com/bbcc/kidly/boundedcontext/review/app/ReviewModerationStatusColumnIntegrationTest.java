package com.bbcc.kidly.boundedcontext.review.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 검사 상태 칸(review.moderation_status)에는 DB 기본값이 없다: JPA를 거치지 않고 칸 없이 넣으면 NOT NULL 위반으로 실패한다
 * (검사 없이 통과로 들어가지 않게). 새 리뷰의 PENDING은 엔티티가 넣는다.
 */
@IntegrationTest
class ReviewModerationStatusColumnIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("moderation_status 없이 리뷰를 직접 넣으면 NOT NULL 위반이고, 값을 넣으면 들어간다")
    void noDefault() {
        assertThat(jdbcTemplate.queryForObject("select column_default from information_schema.columns "
            + "where table_schema = 'community' and table_name = 'review' and column_name = 'moderation_status'",
            String.class)).isNull();
        assertThatThrownBy(() -> jdbcTemplate.update("insert into community.review (order_item_id, product_id, "
            + "member_id, nickname, rating, content, created_at, updated_at) values (11, 14, 13, '닉', 4, '본문', now(), "
            + "now())"))
            .isInstanceOf(DataIntegrityViolationException.class)
            .hasMessageContaining("moderation_status");

        jdbcTemplate.update("insert into community.review (order_item_id, product_id, member_id, nickname, rating, "
            + "content, created_at, updated_at, moderation_status) values (11, 14, 13, '닉', 4, '본문', now(), now(), "
            + "'PENDING')");
        assertThat(jdbcTemplate.queryForObject("select moderation_status from community.review", String.class))
            .isEqualTo("PENDING");
    }
}
