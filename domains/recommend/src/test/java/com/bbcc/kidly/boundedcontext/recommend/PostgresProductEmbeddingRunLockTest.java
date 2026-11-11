package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bbcc.kidly.boundedcontext.recommend.out.embedding.PostgresProductEmbeddingRunLock;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 잠금을 풀지 못한 연결은 풀로 돌려보내지 않고 버려서, 잠금을 쥔 채 풀에 남아 다음 실행을 계속 막는 일이 없게 한다.
 * 해제 SQL의 실패는 실제 DB에서 만들 수 없어서 JDBC를 대역으로 확인한다.
 */
class PostgresProductEmbeddingRunLockTest {

    private final DataSource dataSource = mock(DataSource.class);
    private final Connection connection = mock(Connection.class);
    private final PostgresProductEmbeddingRunLock runLock = new PostgresProductEmbeddingRunLock(dataSource);
    private final AtomicBoolean workRan = new AtomicBoolean();

    @BeforeEach
    void lockAcquired() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);
        PreparedStatement lock = statementReturning(true);
        when(connection.prepareStatement(contains("pg_try_advisory_lock"))).thenReturn(lock);
    }

    @Test
    @DisplayName("잠금을 풀면 연결을 버리지 않고 그대로 돌려준다")
    void returnConnectionWhenUnlocked() throws SQLException {
        PreparedStatement unlock = statementReturning(true);
        when(connection.prepareStatement(contains("pg_advisory_unlock"))).thenReturn(unlock);

        assertThat(runLock.runExclusively(() -> workRan.set(true))).isTrue();

        assertThat(workRan).isTrue();
        verify(connection, never()).abort(any(Executor.class));
        verify(connection).close();
    }

    @Test
    @DisplayName("잠금 해제 SQL이 실패하면 그 연결을 버린다. 일은 끝났으므로 true를 돌려준다")
    void abortConnectionWhenUnlockFails() throws SQLException {
        when(connection.prepareStatement(contains("pg_advisory_unlock"))).thenThrow(new SQLException("unlock"));

        assertThat(runLock.runExclusively(() -> workRan.set(true))).isTrue();

        assertThat(workRan).isTrue();
        verify(connection).abort(any(Executor.class));
    }

    @Test
    @DisplayName("잠금 해제가 false(쥐고 있지 않음)를 돌려줘도 그 연결을 버린다")
    void abortConnectionWhenUnlockReturnsFalse() throws SQLException {
        PreparedStatement unlock = statementReturning(false);
        when(connection.prepareStatement(contains("pg_advisory_unlock"))).thenReturn(unlock);

        runLock.runExclusively(() -> workRan.set(true));

        verify(connection).abort(any(Executor.class));
    }

    @Test
    @DisplayName("일이 예외로 끝나고 잠금 해제도 실패하면 일의 예외를 그대로 던지고 그 연결을 버린다")
    void throwWorkExceptionAndAbortConnectionWhenBothFail() throws SQLException {
        when(connection.prepareStatement(contains("pg_advisory_unlock"))).thenThrow(new SQLException("unlock"));

        assertThatThrownBy(() -> runLock.runExclusively(() -> {
            throw new IllegalArgumentException("작업 실패");
        })).isInstanceOf(IllegalArgumentException.class).hasMessage("작업 실패");

        verify(connection).abort(any(Executor.class));
    }

    @Test
    @DisplayName("해제 실패로 연결을 버린 뒤 닫기가 던지는 예외는 삼키고, 일은 끝났으므로 true를 돌려준다")
    void returnTrueWhenCloseFailsAfterAbort() throws SQLException {
        when(connection.prepareStatement(contains("pg_advisory_unlock"))).thenThrow(new SQLException("unlock"));
        doThrow(new SQLException("close")).when(connection).close();

        assertThat(runLock.runExclusively(() -> workRan.set(true))).isTrue();

        assertThat(workRan).isTrue();
        verify(connection).abort(any(Executor.class));
        verify(connection).close();
    }

    private static PreparedStatement statementReturning(boolean value) throws SQLException {
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet result = mock(ResultSet.class);
        when(statement.executeQuery()).thenReturn(result);
        when(result.next()).thenReturn(true);
        when(result.getBoolean(1)).thenReturn(value);
        return statement;
    }
}
