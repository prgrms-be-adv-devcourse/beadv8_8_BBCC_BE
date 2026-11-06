package com.bbcc.kidly.boundedcontext.notification.in.web;

import com.bbcc.kidly.boundedcontext.notification.app.InboxNotificationService;
import com.bbcc.kidly.boundedcontext.notification.in.web.dto.InboxNotificationResponse;
import com.bbcc.kidly.boundedcontext.notification.in.web.dto.UnreadCountResponse;
import com.bbcc.kidly.global.response.PageResponse;
import com.bbcc.kidly.global.security.AuthChild;
import com.bbcc.kidly.global.security.LoginChild;
import com.bbcc.kidly.shared.auth.PrincipalId;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 자녀 알림함 API. 모두 자녀 로그인 필요(받는 사람 = 자녀 계정). 회원 알림함과 같은 응답 · 규칙이고,
 * 자녀 계정의 알림만 보고 바꾼다. 오류: 404 NOTIFICATION_NOT_FOUND.
 */
@RestController
@RequestMapping("/api/v1/child/notifications")
@RequiredArgsConstructor
public class ChildInboxNotificationController {

    private final InboxNotificationService inboxNotificationService;

    /**
     * 내 알림 최신순. size는 1~100.
     */
    @GetMapping
    public PageResponse<InboxNotificationResponse> findMine(
        @LoginChild AuthChild child,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.from(
            inboxNotificationService.findMine(principal(child), page, size).map(InboxNotificationResponse::from));
    }

    @GetMapping("/unread-count")
    public UnreadCountResponse countUnread(@LoginChild AuthChild child) {
        return new UnreadCountResponse(inboxNotificationService.countUnread(principal(child)));
    }

    /**
     * 읽음 처리. 204. 이미 읽었어도 204.
     */
    @PatchMapping("/{notificationId}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void read(@LoginChild AuthChild child, @PathVariable Long notificationId) {
        inboxNotificationService.read(principal(child), notificationId);
    }

    /**
     * 내 안 읽은 알림을 모두 읽음 처리. 204. 안 읽은 알림이 없어도 204.
     */
    @PatchMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void readAll(@LoginChild AuthChild child) {
        inboxNotificationService.readAll(principal(child));
    }

    private static PrincipalId principal(AuthChild child) {
        return PrincipalId.child(child.accountId());
    }
}
