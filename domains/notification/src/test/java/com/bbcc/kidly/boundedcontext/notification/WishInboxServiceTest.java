package com.bbcc.kidly.boundedcontext.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.bbcc.kidly.boundedcontext.notification.app.ChildAccountService;
import com.bbcc.kidly.boundedcontext.notification.app.InboxNotificationService;
import com.bbcc.kidly.boundedcontext.notification.app.ProductNameService;
import com.bbcc.kidly.boundedcontext.notification.app.WishInboxService;
import com.bbcc.kidly.boundedcontext.notification.app.dto.ChildWishCommand;
import com.bbcc.kidly.boundedcontext.notification.app.dto.WishRejectedCommand;
import com.bbcc.kidly.boundedcontext.notification.app.dto.WishSentCommand;
import com.bbcc.kidly.boundedcontext.notification.domain.InboxNotificationInfo;
import com.bbcc.kidly.boundedcontext.notification.domain.InboxRecipient;
import com.bbcc.kidly.boundedcontext.notification.domain.NotificationType;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WishInboxServiceTest {

    @Mock
    private InboxNotificationService inboxNotificationService;

    @Mock
    private ProductNameService productNameService;

    @Mock
    private ChildAccountService childAccountService;

    private WishInboxService wishInboxService;

    @BeforeEach
    void setUp() {
        wishInboxService = new WishInboxService(inboxNotificationService, productNameService, childAccountService);
    }

    @Test
    @DisplayName("보호자 알림함에 첫 상품 이름과 나머지 개수를 넣고, 조르기 상세로 가는 주소와 조르기 ID 중복 키를 둔다")
    void addWishSent() {
        given(productNameService.findName(10L)).willReturn(Optional.of("무지 티셔츠"));

        wishInboxService.addWishSent(new WishSentCommand(7L, 3L, 1L, Arrays.asList(null, 10L, 11L)));

        var info = captured();
        assertThat(info.recipient()).isEqualTo(InboxRecipient.member(1L));
        assertThat(info.type()).isEqualTo(NotificationType.WISH_SENT);
        assertThat(info.title()).isEqualTo("아이가 구매 요청을 보냈어요");
        assertThat(info.body()).isEqualTo("조른 상품: 무지 티셔츠 외 2개");
        assertThat(info.linkPath()).isEqualTo("/my/children/3/wishes/7");
        assertThat(info.dedupKey()).isEqualTo("wish.sent:7");
    }

    @Test
    @DisplayName("상품 이름 복사본이 없으면 개수만 쓴다")
    void addWishSentWithoutName() {
        given(productNameService.findName(10L)).willReturn(Optional.empty());

        wishInboxService.addWishSent(new WishSentCommand(7L, 3L, 1L, Arrays.asList(10L)));

        assertThat(captured().body()).isEqualTo("조른 상품 1개");
    }

    @Test
    @DisplayName("거절은 자녀 프로필의 활성 계정 알림함에 사유를 쉬운 말로 넣고, 자녀 조르기 주소와 조르기 ID 중복 키를 둔다")
    void addWishRejected() {
        given(childAccountService.findActiveAccountId(3L)).willReturn(Optional.of(30L));
        given(productNameService.findName(10L)).willReturn(Optional.of("무지 티셔츠"));

        wishInboxService.addWishRejected(new WishRejectedCommand(7L, 3L, Arrays.asList(null, 10L), "TOO_EXPENSIVE"));

        var info = captured();
        assertThat(info.recipient()).isEqualTo(InboxRecipient.child(30L));
        assertThat(info.type()).isEqualTo(NotificationType.WISH_REJECTED);
        assertThat(info.title()).isEqualTo("이번 조르기는 들어주기 어렵대요");
        assertThat(info.body()).isEqualTo("지금 사기에는 가격이 많이 비싸대요. 조른 상품: 무지 티셔츠 외 1개");
        assertThat(info.linkPath()).isEqualTo("/child/wishes/7");
        assertThat(info.dedupKey()).isEqualTo("wish.rejected:7");
    }

    @Test
    @DisplayName("모르는 사유나 사유가 없으면 기타 문구를 쓴다")
    void addWishRejectedWithUnknownReason() {
        given(childAccountService.findActiveAccountId(3L)).willReturn(Optional.of(30L));
        given(productNameService.findName(10L)).willReturn(Optional.empty());

        wishInboxService.addWishRejected(new WishRejectedCommand(7L, 3L, List.of(10L), "NEW_REASON"));

        assertThat(captured().body()).isEqualTo("자세한 이유는 부모님께 물어봐요. 조른 상품 1개");
    }

    @Test
    @DisplayName("자녀 프로필에 활성 계정이 없으면 자녀 알림을 넣지 않는다")
    void skipWishRejectedWithoutAccount() {
        given(childAccountService.findActiveAccountId(3L)).willReturn(Optional.empty());

        wishInboxService.addWishRejected(new WishRejectedCommand(7L, 3L, List.of(10L), "NEXT_TIME"));

        verify(inboxNotificationService, never()).add(any());
    }

    @Test
    @DisplayName("결제 완료는 자녀 활성 계정 알림함에 결제한 상품과 함께 넣고, 조르기 ID 중복 키를 둔다")
    void addWishPaid() {
        given(childAccountService.findActiveAccountId(3L)).willReturn(Optional.of(30L));
        given(productNameService.findName(10L)).willReturn(Optional.of("무지 티셔츠"));

        wishInboxService.addWishPaid(new ChildWishCommand(7L, 3L, List.of(10L)));

        var info = captured();
        assertThat(info.recipient()).isEqualTo(InboxRecipient.child(30L));
        assertThat(info.type()).isEqualTo(NotificationType.WISH_PAID);
        assertThat(info.title()).isEqualTo("조르기를 들어줬어요!");
        assertThat(info.body()).isEqualTo("부모님이 결제했어요. 산 상품: 무지 티셔츠");
        assertThat(info.linkPath()).isEqualTo("/child/wishes/7");
        assertThat(info.dedupKey()).isEqualTo("wish.paid:7");
    }

    @Test
    @DisplayName("만료는 자녀 활성 계정 알림함에 조른 상품과 함께 넣고, 조르기 ID 중복 키를 둔다")
    void addWishExpired() {
        given(childAccountService.findActiveAccountId(3L)).willReturn(Optional.of(30L));
        given(productNameService.findName(10L)).willReturn(Optional.empty());

        wishInboxService.addWishExpired(new ChildWishCommand(7L, 3L, List.of(10L, 11L)));

        var info = captured();
        assertThat(info.recipient()).isEqualTo(InboxRecipient.child(30L));
        assertThat(info.type()).isEqualTo(NotificationType.WISH_EXPIRED);
        assertThat(info.title()).isEqualTo("조르기 기한이 지났어요");
        assertThat(info.body()).isEqualTo("부모님 답을 받지 못하고 기한이 지났어요. 조른 상품 2개");
        assertThat(info.dedupKey()).isEqualTo("wish.expired:7");
    }

    @Test
    @DisplayName("자녀 프로필에 활성 계정이 없으면 결제 완료 · 만료 알림도 넣지 않는다")
    void skipWishPaidAndExpiredWithoutAccount() {
        given(childAccountService.findActiveAccountId(3L)).willReturn(Optional.empty());

        wishInboxService.addWishPaid(new ChildWishCommand(7L, 3L, List.of(10L)));
        wishInboxService.addWishExpired(new ChildWishCommand(7L, 3L, List.of(10L)));

        verify(inboxNotificationService, never()).add(any());
    }

    private InboxNotificationInfo captured() {
        var info = ArgumentCaptor.forClass(InboxNotificationInfo.class);
        verify(inboxNotificationService).add(info.capture());
        return info.getValue();
    }
}
