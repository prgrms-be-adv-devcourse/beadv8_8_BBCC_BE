package com.bbcc.kidly.boundedcontext.review.out.store;

import com.bbcc.kidly.boundedcontext.review.app.port.ModerationRunLock;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 검사 실행 잠금: PostgreSQL 세션 advisory lock(pg_try_advisory_lock). 실행하는 동안 따로 받은 연결 하나로 잠금을 쥐고(트랜잭션
 * 밖, 검사기 호출 · 결과 반영은 다른 연결로 한다), 끝나면 풀고 연결을 돌려준다. 풀지 못하면 그 연결을 버린다.
 * 서버가 죽어 연결이 끊기면 DB가 잠금을 푼다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ModerationRunLockStore implements ModerationRunLock {

    private static final String KEY = "kidly.community.review.moderation";

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
            throw new IllegalStateException("리뷰 검사 실행 잠금을 쓰지 못했습니다", e);
        }
    }

    /**
     * 잠금을 푼다. 풀지 못하면(해제 SQL 실패 · 쥐고 있지 않음) 그 연결을 풀로 돌려보내지 않고 버린다(abort).
     * 잠금을 쥔 채 풀에 남으면 다음 실행이 계속 건너뛰기 때문이다. 연결이 끊기면 DB가 잠금을 푼다.
     */
    private static void release(Connection connection) {
        try {
            if (call(connection, "select pg_advisory_unlock(hashtextextended(?, 0))")) {
                return;
            }
            log.warn("리뷰 검사 실행 잠금이 풀리지 않아 연결을 버립니다");
        } catch (SQLException e) {
            log.warn("리뷰 검사 실행 잠금을 풀지 못해 연결을 버립니다. error={}", e.getClass().getSimpleName());
        }
        try {
            connection.abort(Runnable::run);
        } catch (SQLException e) {
            log.warn("리뷰 검사 실행 잠금 연결을 버리지 못했습니다. error={}", e.getClass().getSimpleName());
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
