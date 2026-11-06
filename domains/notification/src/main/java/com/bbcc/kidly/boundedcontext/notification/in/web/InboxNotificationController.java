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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 회원 알림함 API. 모두 회원 로그인 필요. 오류: 404 NOTIFICATION_NOT_FOUND.
 * 자녀 알림함은 ChildInboxNotificationController가 같은 기능을 자녀 계정으로 연다.
 */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class InboxNotificationController {

    private final InboxNotificationService inboxNotificationService;

    /**
     * 내 알림 최신순. size는 1~100.
     */
    @GetMapping
    public PageResponse<InboxNotificationResponse> findMine(
        @LoginMember AuthMember member,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.from(
            inboxNotificationService.findMine(principal(member), page, size).map(InboxNotificationResponse::from));
    }

    @GetMapping("/unread-count")
    public UnreadCountResponse countUnread(@LoginMember AuthMember member) {
        return new UnreadCountResponse(inboxNotificationService.countUnread(principal(member)));
    }

    /**
     * 읽음 처리. 204. 이미 읽었어도 204.
     */
    @PatchMapping("/{notificationId}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void read(@LoginMember AuthMember member, @PathVariable Long notificationId) {
        inboxNotificationService.read(principal(member), notificationId);
    }

    /**
     * 내 안 읽은 알림을 모두 읽음 처리. 204. 안 읽은 알림이 없어도 204.
     */
    @PatchMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void readAll(@LoginMember AuthMember member) {
        inboxNotificationService.readAll(principal(member));
    }

    private static PrincipalId principal(AuthMember member) {
        return PrincipalId.member(member.id());
    }
}
