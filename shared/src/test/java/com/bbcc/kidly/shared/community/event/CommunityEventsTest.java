package com.bbcc.kidly.shared.community.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 커뮤니티 이벤트의 공통 칸: eventType · producer · aggregateId · correlationId(Kafka 키). 찜은 같은 회원의 순서를 지키도록 회원 ID가 키다.
 */
class CommunityEventsTest {

    private static final UUID ID = UUID.randomUUID();
    private static final LocalDateTime AT = LocalDateTime.of(2026, 11, 12, 10, 0);

    @Test
    @DisplayName("review.changed: 대상 · 키는 리뷰 ID, 발행 도메인 community")
    void reviewChanged() {
        var event = new ReviewChangedEvent(ID, AT, new ReviewChangedEvent.Data(7L, 11L, 14L, 4, null, true));

        assertThat(event.eventType()).isEqualTo("review.changed");
        assertThat(event.producer()).isEqualTo("community");
        assertThat(event.aggregateId()).isEqualTo("7");
        assertThat(event.correlationId()).isEqualTo("7");
        assertThat(event.schemaVersion()).isEqualTo(1);
    }

    @Test
    @DisplayName("favorite.added · favorite.removed: 대상은 찜 ID, 키는 회원 ID")
    void favorites() {
        var added = new FavoriteAddedEvent(ID, AT, new FavoriteAddedEvent.Data(3L, 13L, 14L));
        var removed = new FavoriteRemovedEvent(ID, AT,
            new FavoriteRemovedEvent.Data(3L, 13L, 14L, FavoriteRemovalCause.MEMBER_WITHDRAWN));

        assertThat(added.eventType()).isEqualTo("favorite.added");
        assertThat(removed.eventType()).isEqualTo("favorite.removed");
        assertThat(added.producer()).isEqualTo("community");
        assertThat(added.aggregateId()).isEqualTo("3");
        assertThat(added.correlationId()).isEqualTo("13");
        assertThat(removed.aggregateId()).isEqualTo("3");
        assertThat(removed.correlationId()).isEqualTo("13");
    }

    @Test
    @DisplayName("data에 닉네임 · 본문 · 사진 주소 칸이 없고, 찜 까닭은 USER · MEMBER_WITHDRAWN 두 값이다")
    void noPersonalContent() {
        for (Class<?> data : new Class<?>[] {ReviewChangedEvent.Data.class, FavoriteAddedEvent.Data.class,
            FavoriteRemovedEvent.Data.class}) {
            assertThat(Arrays.stream(data.getRecordComponents()).map(component -> component.getName()))
                .noneMatch(name -> name.contains("nickname") || name.contains("content")
                    || name.toLowerCase().contains("url") || name.contains("image"));
        }
        assertThat(FavoriteRemovalCause.values()).extracting(Enum::name)
            .containsExactly("USER", "MEMBER_WITHDRAWN");
    }
}
