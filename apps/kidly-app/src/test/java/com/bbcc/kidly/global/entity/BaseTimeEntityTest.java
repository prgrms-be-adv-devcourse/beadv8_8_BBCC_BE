package com.bbcc.kidly.global.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.TestcontainersConfiguration;
import com.bbcc.kidly.global.config.JpaAuditingConfig;
import com.bbcc.kidly.global.config.QuerydslConfig;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

// JPA Auditing만 확인하므로 @IntegrationTest 대신 JPA 슬라이스로 띄운다.
// 슬라이스는 모든 리포지토리를 띄우고, 리포지토리의 QueryDSL 조각이 JPAQueryFactory를 받으므로 QuerydslConfig도 올린다
@Tag("integration")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, JpaAuditingConfig.class, QuerydslConfig.class})
@ActiveProfiles("test")
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
        Long id = entityManager.persistFlushFind(new AuditTestEntity("처음")).getId();
        entityManager.clear();
        // 관리 중인 엔티티는 flush 때 updatedAt이 덮어써지므로 수정 전 값을 따로 둔다.
        // DB에서 다시 읽은 값(마이크로초)을 써야 OS마다 다른 시계 정밀도에 흔들리지 않는다
        AuditTestEntity before = entityManager.find(AuditTestEntity.class, id);
        LocalDateTime createdAt = before.getCreatedAt();
        LocalDateTime updatedAt = before.getUpdatedAt();

        before.rename("수정");
        entityManager.flush();
        entityManager.clear();
        AuditTestEntity after = entityManager.find(AuditTestEntity.class, id);

        assertThat(after.getCreatedAt()).isEqualTo(createdAt);
        assertThat(after.getUpdatedAt()).isAfter(updatedAt);
    }
}
