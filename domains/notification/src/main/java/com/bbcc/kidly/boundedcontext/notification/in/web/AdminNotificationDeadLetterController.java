package com.bbcc.kidly.boundedcontext.notification.in.web;

import com.bbcc.kidly.boundedcontext.notification.app.NotificationDeadLetterService;
import com.bbcc.kidly.boundedcontext.notification.in.web.dto.NotificationDeadLetterResponse;
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
 * 알림 DLT 보관 목록 · 상세 · 재처리. /api/v1/admin/** 라 관리자 주체만 호출할 수 있다.
 */
@RestController
@RequestMapping("/api/v1/admin/notification-dead-letters")
@RequiredArgsConstructor
public class AdminNotificationDeadLetterController {

    private final NotificationDeadLetterService service;

    // status 기본 OPEN. OPEN · REPROCESSED 밖이면 400
    @GetMapping
    public PageResponse<NotificationDeadLetterResponse> list(
        @RequestParam(required = false) String status,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.from(service.list(status, page, size).map(NotificationDeadLetterResponse::from));
    }

    @GetMapping("/{id}")
    public NotificationDeadLetterResponse get(@PathVariable Long id) {
        return NotificationDeadLetterResponse.from(service.get(id));
    }

    /**
     * 처리 실패도 200/OPEN으로 이력을 반환한다. 이미 성공했으면 재실행 없이 200이다.
     */
    @PostMapping("/{id}/reprocess")
    public NotificationDeadLetterResponse reprocess(@PathVariable Long id, @LoginAdmin AuthAdmin admin) {
        return NotificationDeadLetterResponse.from(service.reprocess(id, admin.id()));
    }
}
