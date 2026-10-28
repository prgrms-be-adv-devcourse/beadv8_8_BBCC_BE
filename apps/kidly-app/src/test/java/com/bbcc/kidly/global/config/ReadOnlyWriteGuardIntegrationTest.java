package com.bbcc.kidly.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.support.IntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 트랜잭션이 끝나면 DB 연결을 돌려주는 설정에서는 readOnly가 DB까지 전달되지 않는다.
 * 그래서 읽기 전용 트랜잭션에서 Hibernate가 보내는 쓰기 SQL을 애플리케이션이 막는지 실제 PostgreSQL로 확인한다.
 * 쓰기 대상은 테스트 전용 엔티티이고, 결과는 트랜잭션 밖에서 DB를 직접 읽어 확인한다.
 */
@IntegrationTest
class ReadOnlyWriteGuardIntegrationTest {

    private static final String RENAME = "update ReadOnlyGuardTestEntity e set e.name = 'after' where e.id = :id";

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("읽기 전용 트랜잭션에서 UPDATE 쿼리를 보내면 예외가 나고 값은 그대로다")
    void rejectUpdateQueryInReadOnlyTransaction() {
        long id = saveEntity("before");

        assertThatThrownBy(() -> readOnly().executeWithoutResult(status ->
            entityManager.createQuery(RENAME).setParameter("id", id).executeUpdate()))
            .hasStackTraceContaining("읽기 전용 트랜잭션");
        assertThat(nameOf(id)).isEqualTo("before");
    }

    @Test
    @DisplayName("읽기 전용 트랜잭션에서 앞에 주석이 붙은 쓰기 SQL도 예외가 나고 값은 그대로다")
    void rejectCommentedWriteInReadOnlyTransaction() {
        long id = saveEntity("before");

        assertThatThrownBy(() -> readOnly().executeWithoutResult(status -> entityManager
            .createNativeQuery("/* 이름 바꾸기 */ update read_only_guard_test_entity set name = 'after' where id = :id")
            .setParameter("id", id)
            .executeUpdate()))
            .hasStackTraceContaining("읽기 전용 트랜잭션");
        assertThat(nameOf(id)).isEqualTo("before");
    }

    @Test
    @DisplayName("읽기 전용 트랜잭션에서 새 엔티티를 저장하면 예외가 나고 저장되지 않는다")
    void rejectNewEntityInReadOnlyTransaction() {
        assertThatThrownBy(() -> readOnly().executeWithoutResult(status ->
            entityManager.persist(new ReadOnlyGuardTestEntity("new"))))
            .hasStackTraceContaining("읽기 전용 트랜잭션");
        assertThat(entityCount()).isZero();
    }

    @Test
    @DisplayName("읽기 전용 트랜잭션에서 조회는 그대로 된다")
    void allowSelectInReadOnlyTransaction() {
        long id = saveEntity("before");

        String name = readOnly().execute(status -> entityManager.find(ReadOnlyGuardTestEntity.class, id).getName());

        assertThat(name).isEqualTo("before");
    }

    @Test
    @DisplayName("읽기 전용이 아닌 트랜잭션의 UPDATE 쿼리는 그대로 반영된다")
    void allowUpdateQueryInReadWriteTransaction() {
        long id = saveEntity("before");

        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
            entityManager.createQuery(RENAME).setParameter("id", id).executeUpdate());

        assertThat(nameOf(id)).isEqualTo("after");
    }

    @Test
    @DisplayName("읽기 전용 트랜잭션 안에서 REQUIRES_NEW로 연 트랜잭션의 저장은 그대로 반영된다")
    void allowSaveInRequiresNewInsideReadOnlyTransaction() {
        readOnly().executeWithoutResult(status -> requiresNew().executeWithoutResult(inner ->
            entityManager.persist(new ReadOnlyGuardTestEntity("new"))));

        assertThat(entityCount()).isEqualTo(1L);
    }

    private TransactionTemplate readOnly() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setReadOnly(true);
        return template;
    }

    // 바깥 트랜잭션을 멈추고 읽기 전용이 아닌 새 트랜잭션을 연다
    private TransactionTemplate requiresNew() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    // 일반 트랜잭션에서 엔티티를 저장하고 커밋한 뒤 ID를 돌려준다
    private long saveEntity(String name) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            ReadOnlyGuardTestEntity entity = new ReadOnlyGuardTestEntity(name);
            entityManager.persist(entity);
            return entity.getId();
        });
    }

    private String nameOf(long id) {
        return jdbcTemplate.queryForObject(
            "select name from read_only_guard_test_entity where id = ?", String.class, id);
    }

    private long entityCount() {
        return jdbcTemplate.queryForObject("select count(*) from read_only_guard_test_entity", Long.class);
    }
}
