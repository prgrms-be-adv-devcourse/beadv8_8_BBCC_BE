package com.bbcc.kidly.global.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.TestcontainersConfiguration;
import com.bbcc.kidly.global.config.JpaAuditingConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

// JPA Auditing만 확인하므로 @IntegrationTest 대신 JPA 슬라이스로 띄운다
@Tag("integration")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, JpaAuditingConfig.class})
class BaseTimeEntityTest {

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("저장하면 생성 시각과 수정 시각이 채워진다")
    void persistSetsCreatedAtAndUpdatedAt() {
        AuditTestEntity saved = entityManager.persistFlushFind(new AuditTestEntity("처음"));

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isEqualTo(saved.getCreatedAt());
    }

    @Test
    @DisplayName("수정하면 수정 시각만 바뀌고 생성 시각은 그대로다")
    void updateChangesOnlyUpdatedAt() {
        AuditTestEntity saved = entityManager.persistFlushFind(new AuditTestEntity("처음"));

        saved.rename("수정");
        entityManager.flush();
        entityManager.clear();
        AuditTestEntity found = entityManager.find(AuditTestEntity.class, saved.getId());

        assertThat(found.getCreatedAt()).isEqualTo(saved.getCreatedAt());
        assertThat(found.getUpdatedAt()).isAfter(saved.getUpdatedAt());
    }
}
