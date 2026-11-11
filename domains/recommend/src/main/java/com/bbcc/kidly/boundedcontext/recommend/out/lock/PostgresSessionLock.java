package com.bbcc.kidly.boundedcontext.recommend.out.lock;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;

/**
 * 키 하나에 대한 PostgreSQL 세션 advisory lock으로, 서버가 여러 대여도 한 대만 일을 하게 한다.
 * 일을 하는 동안 따로 받은 연결 하나로 잠금을 쥔다. 일이 길어도 트랜잭션을 열어 두지 않으려고 트랜잭션 잠금이
 * 아니라 세션 잠금을 쓴다. 서버가 죽어 연결이 끊기면 DB가 잠금을 푼다.
 * 로그와 예외 메시지에는 잠금 이름만 넣는다.
 */
@Slf4j
public final class PostgresSessionLock {

    private final DataSource dataSource;
    private final String key;
    private final String name;

    public PostgresSessionLock(DataSource dataSource, String key, String name) {
        this.dataSource = dataSource;
        this.key = key;
        this.name = name;
    }

    /**
     * 잠금을 잡고 work를 실행한 뒤 푼다.
     *
     * @return 잠금을 잡아 실행했으면 true, 다른 서버가 쥐고 있어 건너뛰었으면 false
     */
    public boolean runExclusively(Runnable work) {
        Connection connection = open();
        boolean aborted = false;
        try {
            if (!call(connection, "select pg_try_advisory_lock(hashtextextended(?, 0))")) {
                return false;
            }
            try {
                work.run();
                return true;
            } finally {
                aborted = release(connection);
            }
        } catch (SQLException e) {
            throw new IllegalStateException(name + " 실행 잠금을 쓰지 못했습니다", e);
        } finally {
            close(connection, aborted);
        }
    }

    private Connection open() {
        try {
            return dataSource.getConnection();
        } catch (SQLException e) {
            throw new IllegalStateException(name + " 실행 잠금을 쓰지 못했습니다", e);
        }
    }

    /**
     * 잠금을 푼다. 풀지 못하면(해제 SQL 실패 · 쥐고 있지 않음) 그 연결을 풀로 돌려보내지 않고 버린다(abort).
     * 잠금을 쥔 채 풀에 남으면 다음 실행이 계속 건너뛰기 때문이다.
     *
     * @return 연결을 버렸으면 true
     */
    private boolean release(Connection connection) {
        try {
            if (call(connection, "select pg_advisory_unlock(hashtextextended(?, 0))")) {
                return false;
            }
            log.warn("{} 실행 잠금이 풀리지 않아 연결을 버립니다", name);
        } catch (SQLException e) {
            log.warn("{} 실행 잠금을 풀지 못해 연결을 버립니다. error={}", name, e.getClass().getSimpleName());
        }
        try {
            connection.abort(Runnable::run);
        } catch (SQLException e) {
            log.warn("{} 실행 잠금 연결을 버리지 못했습니다. error={}", name, e.getClass().getSimpleName());
        }
        return true;
    }

    /**
     * 연결을 닫는다. 버린 연결은 커넥션 풀이 닫을 때 다시 던질 수 있고 일은 이미 끝났으므로 그 예외를 삼킨다.
     */
    private void close(Connection connection, boolean aborted) {
        try {
            connection.close();
        } catch (SQLException e) {
            if (!aborted) {
                throw new IllegalStateException(name + " 실행 잠금 연결을 닫지 못했습니다", e);
            }
            log.warn("버린 {} 실행 잠금 연결을 닫지 못했습니다. error={}", name, e.getClass().getSimpleName());
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
