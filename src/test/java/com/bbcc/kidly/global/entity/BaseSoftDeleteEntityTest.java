package com.bbcc.kidly.global.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BaseSoftDeleteEntityTest {

    private static final Instant NOW = Instant.parse("2026-10-08T00:00:00Z");

    @Test
    @DisplayName("새 엔티티는 삭제되지 않은 상태다")
    void newEntityIsNotDeleted() {
        SoftDeleteTestEntity entity = new SoftDeleteTestEntity();

        assertThat(entity.isDeleted()).isFalse();
        assertThat(entity.getDeletedAt()).isNull();
    }

    @Test
    @DisplayName("삭제하면 삭제 시각이 기록된다")
    void deleteSetsDeletedAt() {
        SoftDeleteTestEntity entity = new SoftDeleteTestEntity();

        entity.delete(NOW);

        assertThat(entity.isDeleted()).isTrue();
        assertThat(entity.getDeletedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("이미 삭제된 엔티티를 다시 삭제해도 처음 삭제 시각을 유지한다")
    void deleteTwiceKeepsFirstDeletedAt() {
        SoftDeleteTestEntity entity = new SoftDeleteTestEntity();
        entity.delete(NOW);

        entity.delete(NOW.plus(Duration.ofMinutes(5)));

        assertThat(entity.getDeletedAt()).isEqualTo(NOW);
    }

    private static class SoftDeleteTestEntity extends BaseSoftDeleteEntity {
    }
}
