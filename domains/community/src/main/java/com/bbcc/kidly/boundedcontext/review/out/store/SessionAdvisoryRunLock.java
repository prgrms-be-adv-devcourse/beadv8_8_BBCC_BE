package com.bbcc.kidly.boundedcontext.review.out.store;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;

/**
 * 주기 실행을 서버 한 대에서만 하게 하는 잠금: PostgreSQL 세션 advisory lock(pg_try_advisory_lock). 실행하는 동안 따로 받은 연결 하나로
 * 잠금을 쥐고(트랜잭션 밖, 외부 호출 · 결과 반영은 다른 연결로 한다), 끝나면 풀고 연결을 돌려준다. 풀지 못하면 그 연결을 버린다.
 * 서버가 죽어 연결이 끊기면 DB가 잠금을 푼다. 실행마다 잠금 이름(key)을 따로 둔다.
 */
@Slf4j
abstract class SessionAdvisoryRunLock {

    private final DataSource dataSource;
    private final String key;
    private final String label;

    /**
     * @param key 잠금 이름(실행마다 다르게)
     * @param label 로그 · 예외에 쓰는 실행 이름(예: 리뷰 검사)
     */
    SessionAdvisoryRunLock(DataSource dataSource, String key, String label) {
        this.dataSource = dataSource;
        this.key = key;
        this.label = label;
    }

    /**
     * @return 잠금을 잡아 work를 실행했으면 true, 다른 쪽이 쥐고 있어 건너뛰었으면 false
     */
    public boolean runExclusively(Runnable work) {
        try (Connection connection = dataSource.getConnection()) {
            if (!call(connection, "select pg_try_advisory_lock(hashtextextended(?, 0))")) {
                return false;
            }
            try {
                work.run();
                return true;
            } finally {
                release(connection);
            }
        } catch (SQLException e) {
            throw new IllegalStateException(label + " 실행 잠금을 쓰지 못했습니다", e);
        }
    }

    /**
     * 잠금을 푼다. 풀지 못하면(해제 SQL 실패 · 쥐고 있지 않음) 그 연결을 풀로 돌려보내지 않고 버린다(abort).
     * 잠금을 쥔 채 풀에 남으면 다음 실행이 계속 건너뛰기 때문이다. 연결이 끊기면 DB가 잠금을 푼다.
     */
    private void release(Connection connection) {
        try {
            if (call(connection, "select pg_advisory_unlock(hashtextextended(?, 0))")) {
                return;
            }
            log.warn("{} 실행 잠금이 풀리지 않아 연결을 버립니다", label);
        } catch (SQLException e) {
            log.warn("{} 실행 잠금을 풀지 못해 연결을 버립니다. error={}", label, e.getClass().getSimpleName());
        }
        try {
            connection.abort(Runnable::run);
        } catch (SQLException e) {
            log.warn("{} 실행 잠금 연결을 버리지 못했습니다. error={}", label, e.getClass().getSimpleName());
        }
    }

    private boolean call(Connection connection, String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, key);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && result.getBoolean(1);
            }
        }
    }
}
