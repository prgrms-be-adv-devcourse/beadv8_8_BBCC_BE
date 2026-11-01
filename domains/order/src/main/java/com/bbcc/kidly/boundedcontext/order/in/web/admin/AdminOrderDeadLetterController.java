package com.bbcc.kidly.boundedcontext.order.in.web.admin;

import com.bbcc.kidly.boundedcontext.order.app.deadletter.OrderDeadLetterService;
import com.bbcc.kidly.boundedcontext.order.in.web.admin.dto.CloseDeadLetterRequest;
import com.bbcc.kidly.boundedcontext.order.in.web.admin.dto.OrderDeadLetterResponse;
import com.bbcc.kidly.boundedcontext.order.in.web.admin.dto.OrderDeadLetterSummaryResponse;
import com.bbcc.kidly.global.response.PageResponse;
import com.bbcc.kidly.global.security.AuthMember;
import com.bbcc.kidly.global.security.LoginMember;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 주문 DLT 메시지 API(#601). /api/v1/admin/** 라 ADMIN만 호출할 수 있다(공용 보안 설정).
 * 재시도해도 계속 실패해 DLT로 간 메시지를 보고, 재처리하거나 확인 후 종료한다.
 */
@RestController
@RequestMapping(AdminOrderDeadLetterController.DEAD_LETTERS_PATH)
@RequiredArgsConstructor
public class AdminOrderDeadLetterController {

    public static final String DEAD_LETTERS_PATH = AdminOrderController.ADMIN_ORDERS_PATH + "/dead-letters";

    private final OrderDeadLetterService deadLetterService;

    /**
     * 최근 받은 것부터. status를 주지 않으면 OPEN(사람이 확인할 것)만. failureType · eventType을 주면 그것만. size는 1~100.
     */
    @GetMapping
    public PageResponse<OrderDeadLetterSummaryResponse> getDeadLetters(
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String failureType,
        @RequestParam(required = false) String eventType,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.from(deadLetterService.getDeadLetters(status, failureType, eventType, page, size)
            .map(OrderDeadLetterSummaryResponse::from));
    }

    /**
     * 저장한 칸 전체 + 관련 주문의 지금 상태 + 권장 조치. 없으면 404.
     */
    @GetMapping("/{deadLetterId}")
    public OrderDeadLetterResponse getDeadLetter(@PathVariable Long deadLetterId) {
        return OrderDeadLetterResponse.from(deadLetterService.getDeadLetter(deadLetterId));
    }

    /**
     * 저장한 본문 · 헤더로 원래 처리를 다시 실행한다(원래 토픽에 다시 넣지 않음). 성공하면 REPROCESSED, 실패하면 OPEN 그대로
     * 재처리 횟수 · 실패 이유를 남겨 모두 200. OPEN이 아니면 409, 다른 재처리가 처리 중이면 409.
     */
    @PostMapping("/{deadLetterId}/reprocess")
    public OrderDeadLetterResponse reprocess(@PathVariable Long deadLetterId, @LoginMember AuthMember admin) {
        return OrderDeadLetterResponse.from(deadLetterService.reprocess(deadLetterId, admin.id()));
    }

    /**
     * 확인 후 종료한다(처리하지 않음, 메모 필수). 이미 종료됐으면 그대로 200, 재처리됐으면 409.
     */
    @PostMapping("/{deadLetterId}/close")
    public OrderDeadLetterResponse close(@PathVariable Long deadLetterId, @LoginMember AuthMember admin,
        @Valid @RequestBody CloseDeadLetterRequest request) {
        return OrderDeadLetterResponse.from(deadLetterService.close(deadLetterId, admin.id(), request.memo()));
    }
}
