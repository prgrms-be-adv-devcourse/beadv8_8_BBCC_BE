package com.bbcc.kidly.boundedcontext.wish.in.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bbcc.kidly.boundedcontext.wish.app.WishReservationValidUntilService;
import com.bbcc.kidly.boundedcontext.wish.app.WishReservationValidUntilService.Batch;
import com.bbcc.kidly.boundedcontext.wish.app.config.WishExpiryProperties;
import com.bbcc.kidly.boundedcontext.wish.app.port.WishJobRunner;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.dao.OptimisticLockingFailureException;

/**
 * 기동 때 예약 기한 맞추기(#1008): ID 커서로 배치를 이어 읽고 바뀐 수를 더한다. 실패해도 기동을 막지 않는다.
 */
class WishReservationValidUntilSyncTest {

    private static final int SIZE = WishReservationValidUntilSync.BATCH_SIZE;

    private final WishReservationValidUntilService service = mock(WishReservationValidUntilService.class);
    private final WishExpiryProperties properties = new WishExpiryProperties(true, Duration.ofDays(7),
        Duration.ofHours(1), Duration.ofMinutes(15));
    // 잠금을 잡았다고 보고 그 자리에서 작업을 돌린다
    private final WishJobRunner runNow = (jobName, job) -> {
        job.run();
        return true;
    };

    @Test
    @DisplayName("100건 → 100건 → 0건: 마지막 ID를 다음 커서로 넘기고, 빈 배치에서 멈추며 바뀐 수를 더한다")
    void continuesUntilEmptyBatch() {
        when(service.syncBatch(0L, SIZE)).thenReturn(new Batch(SIZE, 100L, 3));
        when(service.syncBatch(100L, SIZE)).thenReturn(new Batch(SIZE, 230L, 4));
        when(service.syncBatch(230L, SIZE)).thenReturn(new Batch(0, 230L, 0));
        WishReservationValidUntilSync sync = new WishReservationValidUntilSync(service, properties, runNow);

        assertThat(sync.syncAll()).isEqualTo(7);

        InOrder order = inOrder(service);
        order.verify(service).syncBatch(0L, SIZE);
        order.verify(service).syncBatch(100L, SIZE);
        order.verify(service).syncBatch(230L, SIZE);
        order.verifyNoMoreInteractions();
    }

    @Test
    @DisplayName("100건 → 37건: 배치 크기보다 적게 읽으면 다음 배치를 부르지 않는다")
    void stopsAfterShortBatch() {
        when(service.syncBatch(0L, SIZE)).thenReturn(new Batch(SIZE, 100L, 100));
        when(service.syncBatch(100L, SIZE)).thenReturn(new Batch(37, 137L, 5));
        WishReservationValidUntilSync sync = new WishReservationValidUntilSync(service, properties, runNow);

        assertThat(sync.syncAll()).isEqualTo(105);

        verify(service, never()).syncBatch(137L, SIZE);
    }

    @Test
    @DisplayName("배치가 낙관적 락으로 지면 그 배치를 조르기마다 다시 맞추고, 또 겹치는 건만 건너뛴다(예외 없이 다음 건 계속)")
    void retriesConflictingBatchOneByOne() {
        when(service.syncBatch(0L, SIZE)).thenThrow(new OptimisticLockingFailureException("겹침"));
        when(service.requestIds(0L, SIZE)).thenReturn(List.of(1L, 2L, 3L));
        when(service.syncOne(1L)).thenReturn(1);
        when(service.syncOne(2L)).thenThrow(new OptimisticLockingFailureException("또 겹침"));
        when(service.syncOne(3L)).thenReturn(1);
        WishReservationValidUntilSync sync = new WishReservationValidUntilSync(service, properties, runNow);

        assertThat(sync.syncAll()).isEqualTo(2);

        verify(service).syncOne(3L);
        verify(service, never()).syncBatch(3L, SIZE);
        assertThatCode(sync::onReady).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("기동 때 맞추기가 실패해도 예외를 삼켜 기동을 막지 않는다")
    void swallowsFailureOnReady() {
        when(service.syncBatch(anyLong(), anyInt())).thenThrow(new IllegalStateException("DB 끊김"));
        WishReservationValidUntilSync sync = new WishReservationValidUntilSync(service, properties, runNow);

        assertThatCode(sync::onReady).doesNotThrowAnyException();
    }
}
