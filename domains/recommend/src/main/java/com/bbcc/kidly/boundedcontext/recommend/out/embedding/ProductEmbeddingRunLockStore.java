package com.bbcc.kidly.boundedcontext.recommend.out.embedding;

import com.bbcc.kidly.boundedcontext.recommend.app.port.embedding.ProductEmbeddingRunLock;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.stereotype.Component;

/**
 * 상품 임베딩 실행 잠금이다. 추천 서비스는 서버가 1 ~ 3대라 한 대만 임베딩해야 같은 상품을 여러 번 임베딩하지 않는다.
 * PostgreSQL 세션 advisory lock을 실행하는 동안 따로 받은 연결 하나로 쥔다.
 * OpenAI 응답을 기다리는 동안 트랜잭션을 열어 두지 않으려고 트랜잭션 잠금이 아니라 세션 잠금을 쓴다.
 * 서버가 죽어 연결이 끊기면 DB가 잠금을 푼다.
 */
@Component
public class ProductEmbeddingRunLockStore implements ProductEmbeddingRunLock {

    private static final String KEY = "kidly.recommend.product-embedding";

    private final DataSource dataSource;

    public ProductEmbeddingRunLockStore(DataSource dataSource) {
        this.dataSource = dataSource;
    }

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
                call(connection, "select pg_advisory_unlock(hashtextextended(?, 0))");
            }
        } catch (SQLException e) {
            throw new IllegalStateException("상품 임베딩 실행 잠금을 쓰지 못했습니다", e);
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
