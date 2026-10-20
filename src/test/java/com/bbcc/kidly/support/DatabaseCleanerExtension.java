package com.bbcc.kidly.support;

import static org.assertj.core.api.Assertions.fail;

import com.bbcc.kidly.boundedcontext.member.app.AccessTokenRevocationCache;
import java.time.Duration;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * 테스트마다 모든 스키마의 테이블을 비운다. 롤백 대신 커밋하므로 AFTER_COMMIT 이벤트 리스너도 실제처럼 실행된다.
 * 비우기 전에 @Async 작업(applicationTaskExecutor)이 끝나기를 기다린다. ID가 RESTART IDENTITY로 1부터 다시 시작하므로,
 * 이전 테스트의 비동기 작업이 늦게 돌면 다음 테스트의 같은 ID 행을 건드릴 수 있다.
 * 비운 뒤에는 Access Token 무효화 메모리도 비워(reload), 이전 테스트의 무효화가 같은 ID의 다음 회원을 막지 않게 한다.
 */
class DatabaseCleanerExtension implements AfterEachCallback {

    private static final String FIND_TABLES = """
        SELECT quote_ident(schemaname) || '.' || quote_ident(tablename)
        FROM pg_tables
        WHERE schemaname NOT IN ('pg_catalog', 'information_schema')
          AND tablename <> 'flyway_schema_history'
        """;

    private static final Duration ASYNC_TIMEOUT = Duration.ofSeconds(5);
    private static final long POLL_MILLIS = 20;

    @Override
    public void afterEach(ExtensionContext context) {
        ApplicationContext applicationContext = SpringExtension.getApplicationContext(context);
        try {
            awaitAsyncIdle(applicationContext.getBean("applicationTaskExecutor", ThreadPoolTaskExecutor.class));
        } finally {
            truncateAll(applicationContext.getBean(DataSource.class));
            applicationContext.getBean(AccessTokenRevocationCache.class).reload();
        }
    }

    // 실행 중인 작업 없음 + 큐 비어 있음. 제한 시간 안에 안 되면 실패한다(그래도 테이블은 비운다)
    private static void awaitAsyncIdle(ThreadPoolTaskExecutor executor) {
        long deadline = System.nanoTime() + ASYNC_TIMEOUT.toNanos();
        while (executor.getActiveCount() > 0 || !executor.getThreadPoolExecutor().getQueue().isEmpty()) {
            if (System.nanoTime() > deadline) {
                fail("비동기 작업이 %dms 안에 끝나지 않음: 실행 중 %d, 큐 %d", ASYNC_TIMEOUT.toMillis(),
                    executor.getActiveCount(), executor.getThreadPoolExecutor().getQueue().size());
            }
            try {
                Thread.sleep(POLL_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("비동기 작업을 기다리다 중단됨");
            }
        }
    }

    private static void truncateAll(DataSource dataSource) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        List<String> tables = jdbcTemplate.queryForList(FIND_TABLES, String.class);
        if (!tables.isEmpty()) {
            jdbcTemplate.execute("TRUNCATE TABLE " + String.join(", ", tables) + " RESTART IDENTITY CASCADE");
        }
    }
}
