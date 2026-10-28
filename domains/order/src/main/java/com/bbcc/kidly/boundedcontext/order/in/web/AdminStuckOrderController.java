package com.bbcc.kidly.boundedcontext.order.in.web;

import com.bbcc.kidly.boundedcontext.order.app.stuck.StuckOrderService;
import com.bbcc.kidly.boundedcontext.order.in.web.dto.StuckOrderResponse;
import com.bbcc.kidly.global.response.PageResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 멈춘 주문 조회 API(#445). /api/v1/admin/** 라 ADMIN만 호출할 수 있다(공용 보안 설정). 조회만 한다.
 * 기준 시각을 재는 지금(now)은 요청마다 여기서 한 번 정한다.
 */
@RestController
@RequestMapping(AdminStuckOrderController.STUCK_PATH)
@RequiredArgsConstructor
public class AdminStuckOrderController {

    public static final String STUCK_PATH = AdminOrderController.ADMIN_ORDERS_PATH + "/stuck";

    private final StuckOrderService stuckOrderService;

    /**
     * 종류별 건수. 여섯 종류 키를 모두(0 포함) 담는다. 한 주문이 여러 종류에 들 수 있어 합은 멈춘 주문 수가 아니다.
     */
    @GetMapping("/summary")
    public Map<String, Long> getSummary() {
        Map<String, Long> summary = new LinkedHashMap<>();
        stuckOrderService.getSummary(LocalDateTime.now()).forEach((kind, count) -> summary.put(kind.name(), count));
        return summary;
    }

    /**
     * 그 종류의 멈춘 대상을 오래된 것부터(기준 시각, 같으면 대상 ID). kind는 필수(대문자 그대로, 없거나 모르는 값이면 INVALID_STUCK_KIND), size는 1~100.
     */
    @GetMapping
    public PageResponse<StuckOrderResponse> getStuckOrders(
        // 없을 때도 기본 400(영문 · 안내 없음) 대신 받는 값을 안내하는 INVALID_STUCK_KIND로 답하게 서비스가 검사한다
        @RequestParam(required = false) String kind,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.from(stuckOrderService.getStuckOrders(kind, LocalDateTime.now(), page, size)
            .map(StuckOrderResponse::from));
    }
}
