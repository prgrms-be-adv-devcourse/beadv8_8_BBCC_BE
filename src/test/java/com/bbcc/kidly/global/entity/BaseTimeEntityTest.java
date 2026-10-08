package com.bbcc.kidly.global.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.TestcontainersConfiguration;
import com.bbcc.kidly.global.config.JpaAuditingConfig;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, JpaAuditingConfig.class, TestClockConfig.class})
class BaseTimeEntityTest {

    private static final Instant NOW = Instant.parse("2026-10-08T00:00:00Z");

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private TestClock clock;

    @BeforeEach
    void setUp() {
        clock.setInstant(NOW);
    }

    @Test
    @DisplayName("저장하면 생성 시각과 수정 시각이 채워진다")
    void persistSetsCreatedAtAndUpdatedAt() {
        AuditTestEntity saved = entityManager.persistFlushFind(new AuditTestEntity("처음"));

        assertThat(saved.getCreatedAt()).isEqualTo(NOW);
        assertThat(saved.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("수정하면 수정 시각만 바뀌고 생성 시각은 그대로다")
    void updateChangesOnlyUpdatedAt() {
        AuditTestEntity saved = entityManager.persistFlushFind(new AuditTestEntity("처음"));
        Instant later = NOW.plus(Duration.ofMinutes(5));
        clock.setInstant(later);

        saved.rename("수정");
        entityManager.flush();
        entityManager.clear();
        AuditTestEntity found = entityManager.find(AuditTestEntity.class, saved.getId());

        assertThat(found.getCreatedAt()).isEqualTo(NOW);
        assertThat(found.getUpdatedAt()).isEqualTo(later);
    }
}
