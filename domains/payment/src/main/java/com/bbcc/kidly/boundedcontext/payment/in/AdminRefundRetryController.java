package com.bbcc.kidly.boundedcontext.payment.in;

import com.bbcc.kidly.boundedcontext.payment.app.OrderItemRefundRetryService;
import com.bbcc.kidly.boundedcontext.payment.in.dto.RefundRetryResponse;
import com.bbcc.kidly.boundedcontext.payment.in.dto.RefundRetryResultResponse;
import com.bbcc.kidly.global.response.PageResponse;
import com.bbcc.kidly.global.security.AuthAdmin;
import com.bbcc.kidly.global.security.LoginAdmin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자의 환불하지 못한 주문상품 취소 조회 · 다시 처리 · 닫기. /api/v1/admin/**라 관리자만 호출할 수 있다.
 */
@RestController
@RequestMapping("/api/v1/admin/payments/refund-retries")
@RequiredArgsConstructor
public class AdminRefundRetryController {

    private final OrderItemRefundRetryService retryService;

    // 처음 실패한 순서
    @GetMapping
    public PageResponse<RefundRetryResponse> getRetries(
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.from(retryService.getRetries(page, size).map(RefundRetryResponse::from));
    }

    /**
     * 받은 내용 그대로 다시 처리한다. 환불되면 resolved가 true이고, 또 실패하면 false와 바뀐 기록을 준다.
     */
    @PostMapping("/{retryId}/retry")
    public RefundRetryResultResponse retry(@LoginAdmin AuthAdmin admin, @PathVariable Long retryId) {
        return RefundRetryResultResponse.from(retryService.retry(retryId, admin.id()));
    }

    /**
     * 시스템 밖에서 처리했다고 닫는다. 기록만 지우고 실패한 환불은 남는다.
     */
    @DeleteMapping("/{retryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void close(@LoginAdmin AuthAdmin admin, @PathVariable Long retryId) {
        retryService.close(retryId, admin.id());
    }
}
