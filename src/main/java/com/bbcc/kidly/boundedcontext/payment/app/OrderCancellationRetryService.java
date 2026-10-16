package com.bbcc.kidly.boundedcontext.payment.app;

import com.bbcc.kidly.boundedcontext.payment.app.dto.OrderCancelCommand;
import com.bbcc.kidly.boundedcontext.payment.app.dto.OrderCancellationRetryTarget;
import com.bbcc.kidly.boundedcontext.payment.domain.OrderCancellationRetry;
import com.bbcc.kidly.boundedcontext.payment.domain.OrderCancellationRetryRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 처리하다 실패한 주문 취소를 남기고 다시 처리한다. 리스너 실패는 이벤트를 다시 받을 수 없어서 결제 안에서 메운다.
 * 취소 처리는 환불된 결제 · 취소된 주문 기록이 있으면 건너뛰므로 여러 번 실행돼도 한 번만 반영된다.
 */
@Service
@RequiredArgsConstructor
public class OrderCancellationRetryService {

    private final OrderCancellationRetryRepository orderCancellationRetryRepository;
    private final OrderCancellationService orderCancellationService;

    @Transactional
    public void remember(OrderCancelCommand command, RuntimeException failure) {
        LocalDateTime now = LocalDateTime.now();
        orderCancellationRetryRepository.findByOrderId(command.orderId()).ifPresentOrElse(
            retry -> retry.fail(failure.toString(), now),
            () -> orderCancellationRetryRepository.save(OrderCancellationRetry.failed(
                command.orderId(), command.memberId(), command.reason(), failure.toString(), now)));
    }

    @Transactional(readOnly = true)
    public List<OrderCancellationRetryTarget> findTargets() {
        return orderCancellationRetryRepository.findAllByOrderByIdAsc().stream()
            .map(OrderCancellationRetryTarget::from)
            .toList();
    }

    /**
     * 취소를 다시 처리하고(별도 트랜잭션) 기록을 지운다. 실패하면 예외를 그대로 던지고 기록은 남긴다.
     * 처리는 커밋됐는데 기록을 못 지웠으면 다음 실행에서 다시 처리해도 건너뛰고 지운다.
     */
    @Transactional
    public void retry(OrderCancellationRetryTarget target) {
        orderCancellationService.handle(target.command());
        orderCancellationRetryRepository.findById(target.retryId())
            .ifPresent(orderCancellationRetryRepository::delete);
    }

    @Transactional
    public void recordFailure(Long retryId, RuntimeException failure) {
        orderCancellationRetryRepository.findById(retryId)
            .ifPresent(retry -> retry.fail(failure.toString(), LocalDateTime.now()));
    }
}
