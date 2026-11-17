package com.bbcc.kidly.support;

import static org.assertj.core.api.Assertions.fail;

import java.time.Duration;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.junit.jupiter.SpringExtension;

class DatabaseCleanerExtension implements AfterEachCallback {

    private static final String FIND_TABLES = """
        SELECT quote_ident(schemaname) || '.' || quote_ident(tablename)
        FROM pg_tables
        WHERE schemaname = 'product'
          AND tablename <> 'flyway_schema_history_product'
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
        }
    }

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
