package com.bbcc.kidly.boundedcontext.notification.app;

import com.bbcc.kidly.boundedcontext.notification.app.dto.WishRejectedCommand;
import com.bbcc.kidly.boundedcontext.notification.app.dto.WishSentCommand;
import com.bbcc.kidly.boundedcontext.notification.domain.InboxNotificationInfo;
import com.bbcc.kidly.boundedcontext.notification.domain.InboxRecipient;
import com.bbcc.kidly.boundedcontext.notification.domain.NotificationType;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 아이가 보낸 조르기를 보호자 알림함에, 보호자가 거절한 조르기를 자녀 알림함에 넣는다.
 * 같은 조르기의 같은 일은 한 건만 넣는다(중복 키 = 이벤트 종류 + 조르기 ID).
 * 보호자 알림을 누르면 그 자녀의 조르기 상세로, 자녀 알림을 누르면 자녀 화면의 그 조르기로 간다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WishInboxService {

    private final InboxNotificationService inboxNotificationService;
    private final ProductNameService productNameService;
    private final ChildAccountService childAccountService;

    @Transactional
    public void addWishSent(WishSentCommand command) {
        var firstProductName = findFirstProductName(command.productIds());
        var content = WishInbox.compose(firstProductName, command.productIds().size());
        inboxNotificationService.add(new InboxNotificationInfo(InboxRecipient.member(command.memberId()),
            NotificationType.WISH_SENT, content.title(), content.body(),
            "/my/children/%d/wishes/%d".formatted(command.childId(), command.wishRequestId()),
            "wish.sent:" + command.wishRequestId()));
    }

    /**
     * 자녀 프로필의 활성 계정 알림함에 넣는다. 활성 계정이 없으면(만들지 않았거나 지웠거나, 계정 이벤트가 아직 안 왔으면)
     * 넣지 않고 ID만 INFO로 남긴다. 계정이 없는 자녀가 정상이라 재시도 · DLT로 보내지 않는다.
     */
    @Transactional
    public void addWishRejected(WishRejectedCommand command) {
        childAccountService.findActiveAccountId(command.childProfileId()).ifPresentOrElse(accountId -> {
            var content = WishRejectedInbox.compose(command.reason(), findFirstProductName(command.productIds()),
                command.productIds().size());
            inboxNotificationService.add(new InboxNotificationInfo(InboxRecipient.child(accountId),
                NotificationType.WISH_REJECTED, content.title(), content.body(),
                "/child/wishes/" + command.wishRequestId(), "wish.rejected:" + command.wishRequestId()));
        }, () -> log.info("자녀 활성 계정이 없어 조르기 거절 알림을 만들지 않습니다. wishRequestId={} childProfileId={}",
            command.wishRequestId(), command.childProfileId()));
    }

    private Optional<String> findFirstProductName(List<Long> productIds) {
        return productIds.stream()
            .filter(Objects::nonNull)
            .findFirst()
            .flatMap(productNameService::findName);
    }
}
