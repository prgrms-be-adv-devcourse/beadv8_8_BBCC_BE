package com.bbcc.kidly.global.config;

import java.util.List;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 읽기 전용 트랜잭션에서 쓰기 SQL을 보내면 예외를 던진다.
 * 트랜잭션이 끝나면 DB 연결을 돌려주는 설정에서는 readOnly가 DB까지 전달되지 않아, DB가 하던 쓰기 거부를 여기서 대신한다.
 * Hibernate가 SQL을 보내기 직전에 불러서 JPQL · 네이티브 쿼리 · 엔티티 저장이 모두 지나간다.
 * JdbcTemplate으로 직접 보내는 SQL은 지나가지 않는다.
 * Hibernate가 설정의 클래스 이름으로 직접 만들기 때문에 스프링 빈이 아니고, 인자 없는 생성자가 있어야 한다.
 */
public class ReadOnlyWriteGuard implements StatementInspector {

    private static final List<String> WRITE_KEYWORDS = List.of("insert", "update", "delete", "merge");

    @Override
    public String inspect(String sql) {
        if (!TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            return sql;
        }
        String head = stripLeadingComments(sql);
        for (String keyword : WRITE_KEYWORDS) {
            if (head.regionMatches(true, 0, keyword, 0, keyword.length())) {
                throw new IllegalStateException("읽기 전용 트랜잭션에서는 쓰기 SQL을 보낼 수 없습니다");
            }
        }
        return sql;
    }

    // 쿼리에 주석을 달면 SQL이 /* ... */로 시작하므로, 앞의 주석을 건너뛰고 첫 키워드를 본다
    private static String stripLeadingComments(String sql) {
        String rest = sql.stripLeading();
        while (rest.startsWith("/*")) {
            int end = rest.indexOf("*/");
            if (end < 0) {
                return rest;
            }
            rest = rest.substring(end + 2).stripLeading();
        }
        return rest;
    }
}
