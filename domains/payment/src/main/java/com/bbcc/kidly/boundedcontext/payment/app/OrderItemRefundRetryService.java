package com.bbcc.kidly.boundedcontext.payment.app;

import com.bbcc.kidly.boundedcontext.payment.app.dto.OrderItemRefundRetry;
import com.bbcc.kidly.boundedcontext.payment.domain.OrderItemCancellationRetry;
import com.bbcc.kidly.boundedcontext.payment.domain.OrderItemCancellationRetryRepository;
import com.bbcc.kidly.boundedcontext.payment.domain.PaymentErrorCode;
import com.bbcc.kidly.global.error.BusinessException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 환불하지 못한 주문상품 취소를 관리자가 보고, 다시 처리하거나 닫는다.
 * 다시 처리는 받은 내용 그대로 OrderItemCancellationService를 다시 부른다. 환불되면 기록이 지워지고, 또 실패하면 시도 · 오류가 바뀐다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderItemRefundRetryService {

    private final OrderItemCancellationRetryRepository retryRepository;
    private final OrderItemCancellationService cancellationService;

    @Transactional(readOnly = true)
    public Page<OrderItemRefundRetry> getRetries(int page, int size) {
        return retryRepository.findAllByOrderByIdAsc(PageRequest.of(page, size)).map(OrderItemRefundRetry::from);
    }

    /**
     * 처리는 OrderItemCancellationService의 트랜잭션에서 하고, 끝난 뒤 기록을 다시 읽어 결과를 준다.
     *
     * @return 환불돼 기록이 지워졌으면 비어 있다. 또 실패했으면 바뀐 기록
     */
    public Optional<OrderItemRefundRetry> retry(Long retryId, Long adminId) {
        OrderItemRefundRetry target = retryRepository.findWithItemsById(retryId)
            .map(OrderItemRefundRetry::from)
            .orElseThrow(() -> new BusinessException(PaymentErrorCode.REFUND_RETRY_NOT_FOUND));
        log.info("관리자가 환불하지 못한 주문상품 취소를 다시 처리합니다. retryId={} orderId={} cancellationId={} adminId={}",
            retryId, target.orderId(), target.cancellationId(), adminId);
        cancellationService.handle(target.command());
        return retryRepository.findWithItemsById(retryId).map(OrderItemRefundRetry::from);
    }

    /**
     * 관리자가 시스템 밖에서 처리했다(예: 판매자 · 구매자와 확인해 직접 정리). 기록만 지운다. 실패한 환불은 그대로 남는다.
     */
    @Transactional
    public void close(Long retryId, Long adminId) {
        OrderItemCancellationRetry retry = retryRepository.findById(retryId)
            .orElseThrow(() -> new BusinessException(PaymentErrorCode.REFUND_RETRY_NOT_FOUND));
        retryRepository.delete(retry);
        log.info("관리자가 환불하지 못한 주문상품 취소를 닫았습니다. retryId={} orderId={} cancellationId={} lastError={} adminId={}",
            retryId, retry.getOrderId(), retry.getCancellationId(), retry.getLastError(), adminId);
    }
}
