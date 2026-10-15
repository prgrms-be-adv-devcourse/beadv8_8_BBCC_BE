package com.bbcc.kidly.support;

import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * 테스트마다 모든 스키마의 테이블을 비운다. 롤백 대신 커밋하므로 AFTER_COMMIT 이벤트 리스너도 실제처럼 실행된다.
 */
class DatabaseCleanerExtension implements AfterEachCallback {

    private static final String FIND_TABLES = """
        SELECT quote_ident(schemaname) || '.' || quote_ident(tablename)
        FROM pg_tables
        WHERE schemaname NOT IN ('pg_catalog', 'information_schema')
        """;

    @Override
    public void afterEach(ExtensionContext context) {
        DataSource dataSource = SpringExtension.getApplicationContext(context).getBean(DataSource.class);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        List<String> tables = jdbcTemplate.queryForList(FIND_TABLES, String.class);
        if (!tables.isEmpty()) {
            jdbcTemplate.execute("TRUNCATE TABLE " + String.join(", ", tables) + " RESTART IDENTITY CASCADE");
        }
    }
}
