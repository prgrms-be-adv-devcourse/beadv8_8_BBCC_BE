package com.bbcc.kidly.boundedcontext.payment;

import static com.bbcc.kidly.boundedcontext.payment.PaymentFixture.MEMBER_ID;
import static com.bbcc.kidly.boundedcontext.payment.PaymentFixture.ORDER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;

import com.bbcc.kidly.boundedcontext.payment.app.OrderCancellationRetryService;
import com.bbcc.kidly.boundedcontext.payment.app.OrderCancellationService;
import com.bbcc.kidly.boundedcontext.payment.app.dto.OrderCancelCommand;
import com.bbcc.kidly.boundedcontext.payment.app.dto.OrderCancellationRetryTarget;
import com.bbcc.kidly.boundedcontext.payment.domain.OrderCancellationRetry;
import com.bbcc.kidly.boundedcontext.payment.domain.OrderCancellationRetryRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class OrderCancellationRetryServiceTest {

    private static final OrderCancelCommand COMMAND = new OrderCancelCommand(ORDER_ID, MEMBER_ID, "PAYMENT_EXPIRED");
    private static final Long RETRY_ID = 70L;

    @Mock
    private OrderCancellationRetryRepository orderCancellationRetryRepository;

    @Mock
    private OrderCancellationService orderCancellationService;

    @InjectMocks
    private OrderCancellationRetryService orderCancellationRetryService;

    @Test
    @DisplayName("처음 실패한 취소는 시도 1회로 기록한다")
    void rememberNew() {
        given(orderCancellationRetryRepository.findByOrderId(ORDER_ID)).willReturn(Optional.empty());

        orderCancellationRetryService.remember(COMMAND, new IllegalStateException("DB 오류"));

        ArgumentCaptor<OrderCancellationRetry> retry = ArgumentCaptor.forClass(OrderCancellationRetry.class);
        then(orderCancellationRetryRepository).should().save(retry.capture());
        assertThat(retry.getValue().getOrderId()).isEqualTo(ORDER_ID);
        assertThat(retry.getValue().getMemberId()).isEqualTo(MEMBER_ID);
        assertThat(retry.getValue().getReason()).isEqualTo("PAYMENT_EXPIRED");
        assertThat(retry.getValue().getAttemptCount()).isEqualTo(1);
        assertThat(retry.getValue().getLastError()).contains("DB 오류");
    }

    @Test
    @DisplayName("이미 기록된 주문의 취소가 또 실패하면 새로 만들지 않고 시도 횟수를 늘린다")
    void rememberAgain() {
        OrderCancellationRetry existing = retry();
        given(orderCancellationRetryRepository.findByOrderId(ORDER_ID)).willReturn(Optional.of(existing));

        orderCancellationRetryService.remember(COMMAND, new IllegalStateException("락 대기 시간 초과"));

        then(orderCancellationRetryRepository).should(never()).save(any());
        assertThat(existing.getAttemptCount()).isEqualTo(2);
        assertThat(existing.getLastError()).contains("락 대기 시간 초과");
    }

    @Test
    @DisplayName("다시 처리할 취소를 기록 순서대로 준다")
    void findTargets() {
        given(orderCancellationRetryRepository.findAllByOrderByIdAsc()).willReturn(List.of(retry()));

        assertThat(orderCancellationRetryService.findTargets())
            .containsExactly(new OrderCancellationRetryTarget(RETRY_ID, COMMAND));
    }

    @Test
    @DisplayName("다시 처리해 성공하면 기록을 지운다")
    void retrySucceeds() {
        OrderCancellationRetry existing = retry();
        given(orderCancellationRetryRepository.findById(RETRY_ID)).willReturn(Optional.of(existing));

        orderCancellationRetryService.retry(new OrderCancellationRetryTarget(RETRY_ID, COMMAND));

        InOrder inOrder = inOrder(orderCancellationService, orderCancellationRetryRepository);
        inOrder.verify(orderCancellationService).handle(COMMAND);
        inOrder.verify(orderCancellationRetryRepository).delete(existing);
    }

    @Test
    @DisplayName("다시 처리해도 실패하면 예외를 그대로 던지고 기록은 지우지 않는다")
    void retryFails() {
        IllegalStateException failure = new IllegalStateException("DB 오류");
        willThrow(failure).given(orderCancellationService).handle(COMMAND);

        OrderCancellationRetryTarget target = new OrderCancellationRetryTarget(RETRY_ID, COMMAND);

        assertThatThrownBy(() -> orderCancellationRetryService.retry(target)).isSameAs(failure);
        then(orderCancellationRetryRepository).should(never()).delete(any());
    }

    @Test
    @DisplayName("다시 처리한 취소가 실패하면 시도 횟수와 마지막 오류를 남긴다")
    void recordFailure() {
        OrderCancellationRetry existing = retry();
        given(orderCancellationRetryRepository.findById(RETRY_ID)).willReturn(Optional.of(existing));

        orderCancellationRetryService.recordFailure(RETRY_ID, new IllegalStateException("락 대기 시간 초과"));

        assertThat(existing.getAttemptCount()).isEqualTo(2);
        assertThat(existing.getLastError()).contains("락 대기 시간 초과");
    }

    private static OrderCancellationRetry retry() {
        OrderCancellationRetry retry = OrderCancellationRetry.failed(ORDER_ID, MEMBER_ID, "PAYMENT_EXPIRED", "DB 오류",
            LocalDateTime.now());
        ReflectionTestUtils.setField(retry, "id", RETRY_ID);
        return retry;
    }
}
