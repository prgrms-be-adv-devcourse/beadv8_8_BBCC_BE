package com.bbcc.kidly.boundedcontext.order.in.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchRuntimeException;

import com.bbcc.kidly.boundedcontext.order.app.order.dto.OrderEventFailureResult;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

/**
 * 처리 실패를 보관했는지 판정(#541). Kafka 리스너는 보관했으면 처리 완료로 보고, 아니면 다시 던져 재시도 → DLT로 보낸다.
 * 어느 경우든 run은 원래 예외를 그대로 다시 던진다(Spring 경로의 처리 트랜잭션 되돌림 그대로).
 */
class EventFailureKeeperTest {

    private static final Long ORDER_ID = 7L;
    private static final OrderEventFailureResult SAVED = new OrderEventFailureResult(1L, "STOCK_DEDUCTED", ORDER_ID,
        List.of(), "PENDING", "DataIntegrityViolationException", 1, LocalDateTime.now(), LocalDateTime.now());

    @Test
    @DisplayName("처리 실패를 보관했으면 원래 예외를 그대로 던지고, 그 예외는 보관됨으로 판정한다")
    void keptFailure() {
        var failure = new DataIntegrityViolationException("주문 UPDATE 실패");

        RuntimeException thrown = catchRuntimeException(() -> EventFailureKeeper.run("stock.deducted", ORDER_ID,
            () -> {
                throw failure;
            }, error -> SAVED));

        assertThat(thrown).isSameAs(failure);
        assertThat(EventFailureKeeper.isKept(thrown)).isTrue();
    }

    @Test
    @DisplayName("보관까지 실패하면 보관되지 않음으로 판정한다(재시도 → DLT)")
    void recordFailed() {
        var failure = new DataIntegrityViolationException("주문 UPDATE 실패");

        RuntimeException thrown = catchRuntimeException(() -> EventFailureKeeper.run("stock.deducted", ORDER_ID,
            () -> {
                throw failure;
            }, error -> {
                throw new DataIntegrityViolationException("보관 INSERT 실패");
            }));

        assertThat(thrown).isSameAs(failure);
        assertThat(EventFailureKeeper.isKept(thrown)).isFalse();
    }

    @Test
    @DisplayName("동시 처리에서 진 쪽은 보관하지 않으므로 보관되지 않음으로 판정한다(재시도 때 상태 검사로 아무것도 안 함)")
    void lostConcurrentDuplicate() {
        var lost = new ObjectOptimisticLockingFailureException("Order", ORDER_ID);

        RuntimeException thrown = catchRuntimeException(() -> EventFailureKeeper.run("stock.deducted", ORDER_ID,
            () -> {
                throw lost;
            }, error -> SAVED));

        assertThat(EventFailureKeeper.isKept(thrown)).isFalse();
    }

    @Test
    @DisplayName("보관 헬퍼를 거치지 않은 예외(처리 트랜잭션 커밋 실패 · 읽기 실패 등)는 보관되지 않음으로 판정한다")
    void otherException() {
        assertThat(EventFailureKeeper.isKept(new IllegalStateException("커밋 실패"))).isFalse();
    }
}
