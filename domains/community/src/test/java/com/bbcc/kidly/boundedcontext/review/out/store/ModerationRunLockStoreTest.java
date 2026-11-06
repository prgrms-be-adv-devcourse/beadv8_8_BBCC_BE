package com.bbcc.kidly.boundedcontext.review.out.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
 * 잠금을 풀지 못한 연결은 풀로 돌려보내지 않고 버린다(잠금을 쥔 채 풀에 남아 다음 실행을 영영 막지 않게).
 */
class ModerationRunLockStoreTest {

    private final DataSource dataSource = mock(DataSource.class);
    private final Connection connection = mock(Connection.class);
    private final ModerationRunLockStore store = new ModerationRunLockStore(dataSource);
    private final AtomicBoolean ran = new AtomicBoolean();

    @BeforeEach
    void lockAcquired() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);
        PreparedStatement lock = statement(true);
        when(connection.prepareStatement(contains("pg_try_advisory_lock"))).thenReturn(lock);
    }

    @Test
    @DisplayName("잠금을 풀면 연결을 그대로 돌려준다(버리지 않음)")
    void releasesNormally() throws SQLException {
        PreparedStatement unlock = statement(true);
        when(connection.prepareStatement(contains("pg_advisory_unlock"))).thenReturn(unlock);

        assertThat(store.runExclusively(() -> ran.set(true))).isTrue();

        assertThat(ran).isTrue();
        verify(connection, never()).abort(any(Executor.class));
        verify(connection).close();
    }

    @Test
    @DisplayName("잠금 해제 SQL이 실패하면 그 연결을 버린다(abort). 실행한 일은 끝났으므로 true")
    void abortsWhenUnlockFails() throws SQLException {
        when(connection.prepareStatement(contains("pg_advisory_unlock"))).thenThrow(new SQLException("unlock"));

        assertThat(store.runExclusively(() -> ran.set(true))).isTrue();

        assertThat(ran).isTrue();
        verify(connection).abort(any(Executor.class));
    }

    @Test
    @DisplayName("잠금 해제가 false(쥐고 있지 않음)를 돌려줘도 그 연결을 버린다")
    void abortsWhenUnlockReturnsFalse() throws SQLException {
        PreparedStatement unlock = statement(false);
        when(connection.prepareStatement(contains("pg_advisory_unlock"))).thenReturn(unlock);

        store.runExclusively(() -> ran.set(true));

        verify(connection).abort(any(Executor.class));
    }

    @Test
    @DisplayName("일이 예외로 끝나도 잠금을 풀고 예외는 그대로 던진다")
    void unlocksWhenWorkFails() throws SQLException {
        PreparedStatement unlock = statement(true);
        when(connection.prepareStatement(contains("pg_advisory_unlock"))).thenReturn(unlock);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> store.runExclusively(() -> {
            throw new IllegalStateException("work");
        })).isInstanceOf(IllegalStateException.class).hasMessage("work");

        verify(unlock).executeQuery();
        verify(connection, never()).abort(any(Executor.class));
    }

    private static PreparedStatement statement(boolean value) throws SQLException {
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet result = mock(ResultSet.class);
        when(statement.executeQuery()).thenReturn(result);
        when(result.next()).thenReturn(true);
        when(result.getBoolean(1)).thenReturn(value);
        return statement;
    }
}
