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

@RestController
@RequestMapping("/api/v1/admin/notification-dead-letters")
@RequiredArgsConstructor
public class AdminNotificationDeadLetterController {

    private final NotificationDeadLetterService service;

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

    @PostMapping("/{id}/reprocess")
    public NotificationDeadLetterResponse reprocess(@PathVariable Long id, @LoginAdmin AuthAdmin admin) {
        return NotificationDeadLetterResponse.from(service.reprocess(id, admin.id()));
    }
}
