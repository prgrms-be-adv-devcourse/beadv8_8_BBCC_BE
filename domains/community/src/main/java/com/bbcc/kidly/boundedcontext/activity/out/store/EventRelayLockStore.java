package com.bbcc.kidly.boundedcontext.activity.out.store;

import com.bbcc.kidly.boundedcontext.activity.app.port.EventRelayLock;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 이벤트 릴레이 실행 잠금: PostgreSQL 세션 advisory lock(pg_try_advisory_lock, 잠금 이름 kidly.community.event-relay). 실행하는 동안
 * 따로 받은 연결 하나로 잠금을 쥐고(트랜잭션 밖, 보내기 · 결과 반영은 다른 연결로 한다), 끝나면 풀고 연결을 돌려준다. 풀지 못하면
 * 그 연결을 버린다(잠금을 쥔 채 풀에 남아 다음 실행을 막지 않게). 서버가 죽어 연결이 끊기면 DB가 잠금을 푼다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventRelayLockStore implements EventRelayLock {

    private static final String KEY = "kidly.community.event-relay";

    private final DataSource dataSource;

    @Override
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
            throw new IllegalStateException("이벤트 릴레이 실행 잠금을 쓰지 못했습니다", e);
        }
    }

    private static void release(Connection connection) {
        try {
            if (call(connection, "select pg_advisory_unlock(hashtextextended(?, 0))")) {
                return;
            }
            log.warn("이벤트 릴레이 실행 잠금이 풀리지 않아 연결을 버립니다");
        } catch (SQLException e) {
            log.warn("이벤트 릴레이 실행 잠금을 풀지 못해 연결을 버립니다. error={}", e.getClass().getSimpleName());
        }
        try {
            connection.abort(Runnable::run);
        } catch (SQLException e) {
            log.warn("이벤트 릴레이 실행 잠금 연결을 버리지 못했습니다. error={}", e.getClass().getSimpleName());
        }
    }

    private static boolean call(Connection connection, String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, KEY);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && result.getBoolean(1);
            }
        }
    }
}
