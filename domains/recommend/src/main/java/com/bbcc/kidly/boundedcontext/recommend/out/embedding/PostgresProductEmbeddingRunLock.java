package com.bbcc.kidly.boundedcontext.recommend.out.embedding;

import com.bbcc.kidly.boundedcontext.recommend.app.port.embedding.ProductEmbeddingRunLock;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 상품 임베딩 실행 잠금이다. 추천 서비스는 서버가 여러 대라 한 대만 임베딩해야 같은 상품을 여러 번 임베딩하지 않는다.
 * PostgreSQL 세션 advisory lock을 실행하는 동안 따로 받은 연결 하나로 쥔다.
 * OpenAI 응답을 기다리는 동안 트랜잭션을 열어 두지 않으려고 트랜잭션 잠금이 아니라 세션 잠금을 쓴다.
 * 서버가 죽어 연결이 끊기면 DB가 잠금을 푼다.
 */
@Slf4j
@Component
public class PostgresProductEmbeddingRunLock implements ProductEmbeddingRunLock {

    private static final String KEY = "kidly.recommend.product-embedding";

    private final DataSource dataSource;

    public PostgresProductEmbeddingRunLock(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
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
            throw new IllegalStateException("상품 임베딩 실행 잠금을 쓰지 못했습니다", e);
        } finally {
            close(connection, aborted);
        }
    }

    private Connection open() {
        try {
            return dataSource.getConnection();
        } catch (SQLException e) {
            throw new IllegalStateException("상품 임베딩 실행 잠금을 쓰지 못했습니다", e);
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
            log.warn("상품 임베딩 실행 잠금이 풀리지 않아 연결을 버립니다");
        } catch (SQLException e) {
            log.warn("상품 임베딩 실행 잠금을 풀지 못해 연결을 버립니다. error={}", e.getClass().getSimpleName());
        }
        try {
            connection.abort(Runnable::run);
        } catch (SQLException e) {
            log.warn("상품 임베딩 실행 잠금 연결을 버리지 못했습니다. error={}", e.getClass().getSimpleName());
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
                throw new IllegalStateException("상품 임베딩 실행 잠금 연결을 닫지 못했습니다", e);
            }
            log.warn("버린 상품 임베딩 실행 잠금 연결을 닫지 못했습니다. error={}", e.getClass().getSimpleName());
        }
    }

    private boolean call(Connection connection, String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, KEY);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && result.getBoolean(1);
            }
        }
    }
}
