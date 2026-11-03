package com.bbcc.kidly.boundedcontext.order.in.web.admin;

import com.bbcc.kidly.boundedcontext.order.app.outbox.OrderOutboxAdminService;
import com.bbcc.kidly.boundedcontext.order.in.web.admin.dto.OutboxQuarantineResponse;
import com.bbcc.kidly.global.response.PageResponse;
import com.bbcc.kidly.global.security.AuthAdmin;
import com.bbcc.kidly.global.security.LoginAdmin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 격리 아웃박스 확인 · 재시도. 역할 검사는 Gateway가 맡고 재시도 처리자 ID를 기록한다.
 */
@RestController
@RequestMapping(AdminOrderController.ADMIN_ORDERS_PATH + "/outbox")
@RequiredArgsConstructor
public class AdminOrderOutboxController {

    private final OrderOutboxAdminService service;

    @GetMapping("/quarantined")
    public PageResponse<OutboxQuarantineResponse> findQuarantined(
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.from(service.findQuarantined(page, size).map(OutboxQuarantineResponse::from));
    }

    @GetMapping("/{outboxId}")
    public OutboxQuarantineResponse get(@PathVariable Long outboxId) {
        return OutboxQuarantineResponse.from(service.get(outboxId));
    }

    /**
     * 원인 해결 후 원본 이벤트를 다시 전송 대기시킨다. 이미 대기 중이면 그대로 200, 보냈으면 409.
     */
    @PostMapping("/{outboxId}/retry")
    public OutboxQuarantineResponse retry(@PathVariable Long outboxId, @LoginAdmin AuthAdmin admin) {
        return OutboxQuarantineResponse.from(service.retry(outboxId, admin.id()));
    }
}
