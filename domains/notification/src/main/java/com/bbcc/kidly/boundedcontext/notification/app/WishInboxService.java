package com.bbcc.kidly.boundedcontext.notification.app;

import com.bbcc.kidly.boundedcontext.notification.app.dto.WishSentCommand;
import com.bbcc.kidly.boundedcontext.notification.domain.InboxNotificationInfo;
import com.bbcc.kidly.boundedcontext.notification.domain.NotificationType;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 아이가 보낸 조르기를 보호자 알림함에 넣는다. 같은 조르기는 한 건만 넣는다(중복 키 = 조르기 ID).
 * 누르면 그 자녀의 조르기 상세로 간다.
 */
@Service
@RequiredArgsConstructor
public class WishInboxService {

    private final InboxNotificationService inboxNotificationService;
    private final ProductNameService productNameService;

    @Transactional
    public void addWishSent(WishSentCommand command) {
        var firstProductName = command.productIds().stream()
            .filter(Objects::nonNull)
            .findFirst()
            .flatMap(productNameService::findName);
        var content = WishInbox.compose(firstProductName, command.productIds().size());
        inboxNotificationService.add(new InboxNotificationInfo(command.memberId(), NotificationType.WISH_SENT,
            content.title(), content.body(),
            "/my/children/%d/wishes/%d".formatted(command.childId(), command.wishRequestId()),
            "wish.sent:" + command.wishRequestId()));
    }
}
