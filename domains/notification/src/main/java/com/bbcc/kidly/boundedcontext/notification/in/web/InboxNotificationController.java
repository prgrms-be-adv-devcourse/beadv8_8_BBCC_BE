package com.bbcc.kidly.boundedcontext.notification.in.web;

import com.bbcc.kidly.boundedcontext.notification.app.InboxNotificationService;
import com.bbcc.kidly.boundedcontext.notification.in.web.dto.InboxNotificationResponse;
import com.bbcc.kidly.boundedcontext.notification.in.web.dto.UnreadCountResponse;
import com.bbcc.kidly.global.response.PageResponse;
import com.bbcc.kidly.global.security.AuthMember;
import com.bbcc.kidly.global.security.LoginMember;
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
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class InboxNotificationController {

    private final InboxNotificationService inboxNotificationService;
    private final UnreadCountStream unreadCountStream;

    @GetMapping
    public PageResponse<InboxNotificationResponse> findMine(
        @LoginMember AuthMember member,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.from(
            inboxNotificationService.findMine(principal(member), page, size).map(InboxNotificationResponse::from));
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamUnreadCount(@LoginMember AuthMember member) {
        return unreadCountStream.open(principal(member));
    }

    @GetMapping("/unread-count")
    public UnreadCountResponse countUnread(@LoginMember AuthMember member) {
        return new UnreadCountResponse(inboxNotificationService.countUnread(principal(member)));
    }

    @PatchMapping("/{notificationId}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void read(@LoginMember AuthMember member, @PathVariable Long notificationId) {
        inboxNotificationService.read(principal(member), notificationId);
    }

    @PatchMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void readAll(@LoginMember AuthMember member) {
        inboxNotificationService.readAll(principal(member));
    }

    private static PrincipalId principal(AuthMember member) {
        return PrincipalId.member(member.id());
    }
}
