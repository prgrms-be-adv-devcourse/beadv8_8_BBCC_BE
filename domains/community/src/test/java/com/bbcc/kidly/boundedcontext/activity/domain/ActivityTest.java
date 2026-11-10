package com.bbcc.kidly.boundedcontext.activity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.activity.domain.Activity.DecidedBy;
import com.bbcc.kidly.boundedcontext.activity.domain.Activity.RemovalCause;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 활동 종류마다 정해진 속성만 갖고, 속성 키는 ActivityAttribute 한 곳에서 온다. 개인정보 · 자유 문구 키는 없다.
 */
class ActivityTest {

    @Test
    @DisplayName("속성 키는 정해진 snake_case 이름뿐이고 회원 ID · 닉네임 · 본문 · 사진 주소 키는 없다")
    void attributeKeys() {
        Set<String> keys = Arrays.stream(ActivityAttribute.values()).map(ActivityAttribute::key)
            .collect(Collectors.toSet());

        assertThat(keys).containsExactlyInAnyOrder("order_item_id", "rating", "fit", "image_count",
            "content_changed", "images_changed", "was_public", "cause", "by", "status", "reason", "backfilled");
        assertThat(keys).allSatisfy(key -> assertThat(key).matches("[a-z]+(_[a-z]+)*")
            .doesNotContain("member").doesNotContain("nickname").doesNotContain("content_text")
            .doesNotContain("url"));
    }

    @Test
    @DisplayName("종류별 속성: 작성 · 수정 · 삭제 · 찜 · 검사")
    void attributesPerType() {
        assertThat(Activity.eligibilityCreated(1, 14L, 11).attributesByKey())
            .containsExactlyEntriesOf(Map.of("order_item_id", 11L));
        assertThat(Activity.reviewCreated(1, 14L, 11, 4, null, 2).attributesByKey())
            .containsOnlyKeys("order_item_id", "rating", "fit", "image_count").containsEntry("fit", null);
        assertThat(Activity.reviewUpdated(1, 14L, 3, "LARGE", 0, true, false).attributesByKey())
            .containsOnlyKeys("rating", "fit", "image_count", "content_changed", "images_changed");
        assertThat(Activity.reviewDeleted(1, 14L, 3, false).attributesByKey())
            .containsOnlyKeys("rating", "was_public");
        assertThat(Activity.favoriteAdded(1, 14L).attributesByKey()).isEmpty();
        assertThat(Activity.favoriteRemoved(1, 14L, RemovalCause.WITHDRAWN).attributesByKey())
            .containsExactlyEntriesOf(Map.of("cause", "withdrawn"));
        assertThat(Activity.moderationDecided(1, 14L, DecidedBy.ADMIN, "RESTORED", null).attributesByKey())
            .containsOnlyKeys("by", "status", "reason").containsEntry("by", "admin");
    }

    @ParameterizedTest(name = "[{0}]")
    @ValueSource(strings = {"PENDING", "UNKNOWN", ""})
    @DisplayName("검사 결정으로 남기는 상태는 PASSED · HIDDEN · CONFIRMED_HIDDEN · RESTORED뿐이다")
    void moderationStatuses(String status) {
        assertThatThrownBy(() -> Activity.moderationDecided(1, 14L, DecidedBy.MODERATOR, status, null))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
