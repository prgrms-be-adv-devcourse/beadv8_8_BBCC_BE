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
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/child/notifications")
@RequiredArgsConstructor
public class ChildInboxNotificationController {

    private final InboxNotificationService inboxNotificationService;
    private final UnreadCountStream unreadCountStream;

    @GetMapping
    public PageResponse<InboxNotificationResponse> findMine(
        @LoginChild AuthChild child,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.from(
            inboxNotificationService.findMine(principal(child), page, size).map(InboxNotificationResponse::from));
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamUnreadCount(@LoginChild AuthChild child) {
        return unreadCountStream.open(principal(child));
    }

    @GetMapping("/unread-count")
    public UnreadCountResponse countUnread(@LoginChild AuthChild child) {
        return new UnreadCountResponse(inboxNotificationService.countUnread(principal(child)));
    }

    @PatchMapping("/{notificationId}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void read(@LoginChild AuthChild child, @PathVariable Long notificationId) {
        inboxNotificationService.read(principal(child), notificationId);
    }

    @PatchMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void readAll(@LoginChild AuthChild child) {
        inboxNotificationService.readAll(principal(child));
    }

    private static PrincipalId principal(AuthChild child) {
        return PrincipalId.child(child.accountId());
    }
}
