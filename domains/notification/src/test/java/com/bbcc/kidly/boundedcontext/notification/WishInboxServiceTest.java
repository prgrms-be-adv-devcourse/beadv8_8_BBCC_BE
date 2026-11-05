package com.bbcc.kidly.boundedcontext.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.bbcc.kidly.boundedcontext.notification.app.InboxNotificationService;
import com.bbcc.kidly.boundedcontext.notification.app.ProductNameService;
import com.bbcc.kidly.boundedcontext.notification.app.WishInboxService;
import com.bbcc.kidly.boundedcontext.notification.app.dto.WishSentCommand;
import com.bbcc.kidly.boundedcontext.notification.domain.InboxNotificationInfo;
import com.bbcc.kidly.boundedcontext.notification.domain.NotificationType;
import java.util.Arrays;
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

    private WishInboxService wishInboxService;

    @BeforeEach
    void setUp() {
        wishInboxService = new WishInboxService(inboxNotificationService, productNameService);
    }

    @Test
    @DisplayName("보호자 알림함에 첫 상품 이름과 나머지 개수를 넣고, 조르기 상세로 가는 주소와 조르기 ID 중복 키를 둔다")
    void addWishSent() {
        given(productNameService.findName(10L)).willReturn(Optional.of("무지 티셔츠"));

        wishInboxService.addWishSent(new WishSentCommand(7L, 3L, 1L, Arrays.asList(null, 10L, 11L)));

        var info = captured();
        assertThat(info.memberId()).isEqualTo(1L);
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

    private InboxNotificationInfo captured() {
        var info = ArgumentCaptor.forClass(InboxNotificationInfo.class);
        verify(inboxNotificationService).add(info.capture());
        return info.getValue();
    }
}
