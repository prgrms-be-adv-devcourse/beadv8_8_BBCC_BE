package com.bbcc.kidly.gateway.revocation;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.kafka.listener.ConsumerSeekAware.ConsumerSeekCallback;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class AuthTokensRevokedListenerTest {

    private static final TopicPartition P0 = new TopicPartition("kidly.auth.events", 0);
    private static final TopicPartition P1 = new TopicPartition("kidly.auth.events", 1);
    private static final Map<TopicPartition, Long> ASSIGNMENTS = Map.of(P0, 10L, P1, 20L);

    @Mock
    private AccessTokenRevocations revocations;
    @Mock
    private ConsumerSeekCallback callback;
    private AuthTokensRevokedListener listener;

    @BeforeEach
    void setUp() {
        listener = new AuthTokensRevokedListener(revocations, JsonMapper.builder().build());
    }

    @Test
    @DisplayName("표식이 없는 파티션만 되감고 그 파티션에만 표식을 쓴다")
    void rewindsOnlyUnloadedPartition() {
        when(revocations.isLoaded(0)).thenReturn(true);
        when(revocations.isLoaded(1)).thenReturn(false);
        when(revocations.retention()).thenReturn(Duration.ofMinutes(31));

        listener.onPartitionsAssigned(ASSIGNMENTS, callback);

        verify(callback).seekToTimestamp(eq(List.of(P1)), anyLong());
        verify(revocations).markLoaded(1);
        verify(revocations, never()).markLoaded(0);
    }

    @Test
    @DisplayName("파티션을 받을 때 표식을 읽지 못하면 모두 되감는다")
    void rewindsAllOnAssignmentWhenRedisUnavailable() {
        when(revocations.isLoaded(anyInt()))
            .thenThrow(new DataAccessResourceFailureException("unavailable"));
        when(revocations.retention()).thenReturn(Duration.ofMinutes(31));

        listener.onPartitionsAssigned(ASSIGNMENTS, callback);

        verify(callback).seekToTimestamp(eq(ASSIGNMENTS.keySet()), anyLong());
        verify(revocations, never()).markLoaded(anyInt());
    }

    @Test
    @DisplayName("idle 확인에서 표식을 읽지 못하면 아무것도 하지 않는다")
    void doesNothingOnIdleWhenRedisUnavailable() {
        when(revocations.isLoaded(anyInt()))
            .thenThrow(new DataAccessResourceFailureException("unavailable"));

        listener.onIdleContainer(ASSIGNMENTS, callback);

        verifyNoInteractions(callback);
        verify(revocations, never()).markLoaded(anyInt());
    }
}
