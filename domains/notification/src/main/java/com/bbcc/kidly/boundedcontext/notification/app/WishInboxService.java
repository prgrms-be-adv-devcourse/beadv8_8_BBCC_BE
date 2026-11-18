package com.bbcc.kidly.boundedcontext.notification.app;

import com.bbcc.kidly.boundedcontext.notification.app.dto.ChildWishCommand;
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
 * 같은 조르기의 같은 일은 한 건만 넣는다(중복 키 = 이벤트 종류 + 조르기 ID).
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

    @Transactional
    public void addWishPaid(ChildWishCommand command) {
        addToChild(command.wishRequestId(), command.childProfileId(), NotificationType.WISH_PAID, "wish.paid:",
            ChildWishInbox.paid(findFirstProductName(command.productIds()), command.productIds().size()));
    }

    @Transactional
    public void addWishRejected(WishRejectedCommand command) {
        addToChild(command.wishRequestId(), command.childProfileId(), NotificationType.WISH_REJECTED,
            "wish.rejected:", ChildWishInbox.rejected(command.reason(),
                findFirstProductName(command.productIds()), command.productIds().size()));
    }

    @Transactional
    public void addWishExpired(ChildWishCommand command) {
        addToChild(command.wishRequestId(), command.childProfileId(), NotificationType.WISH_EXPIRED, "wish.expired:",
            ChildWishInbox.expired(findFirstProductName(command.productIds()), command.productIds().size()));
    }

    /**
     * 활성 계정이 없으면(만들지 않았거나 지웠거나, 계정 이벤트가 아직 안 왔으면)
     * 넣지 않고 ID만 INFO로 남긴다. 계정이 없는 자녀가 정상이라 재시도 · DLT로 보내지 않는다.
     */
    private void addToChild(Long wishRequestId, Long childProfileId, NotificationType type, String keyPrefix,
        ChildWishInbox content) {
        childAccountService.findActiveAccountId(childProfileId).ifPresentOrElse(accountId ->
                inboxNotificationService.add(new InboxNotificationInfo(InboxRecipient.child(accountId), type,
                    content.title(), content.body(), "/child/wishes/" + wishRequestId, keyPrefix + wishRequestId)),
            () -> log.info("자녀 활성 계정이 없어 조르기 알림을 만들지 않습니다. type={} wishRequestId={} childProfileId={}",
                type, wishRequestId, childProfileId));
    }

    private Optional<String> findFirstProductName(List<Long> productIds) {
        return productIds.stream()
            .filter(Objects::nonNull)
            .findFirst()
            .flatMap(productNameService::findName);
    }
}
