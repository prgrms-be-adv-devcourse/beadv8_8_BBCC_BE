package com.bbcc.kidly.boundedcontext.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.bbcc.kidly.boundedcontext.notification.app.AutoConfirmInboxService;
import com.bbcc.kidly.boundedcontext.notification.app.InboxNotificationService;
import com.bbcc.kidly.boundedcontext.notification.app.ProductNameService;
import com.bbcc.kidly.boundedcontext.notification.app.dto.PurchaseConfirmedCommand;
import com.bbcc.kidly.boundedcontext.notification.domain.InboxNotificationInfo;
import com.bbcc.kidly.boundedcontext.notification.domain.InboxRecipient;
import com.bbcc.kidly.boundedcontext.notification.domain.NotificationType;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AutoConfirmInboxServiceTest {

    @Mock
    private InboxNotificationService inboxNotificationService;

    @Mock
    private ProductNameService productNameService;

    @InjectMocks
    private AutoConfirmInboxService autoConfirmInboxService;

    @Test
    @DisplayName("자동 구매확정은 구매자 알림함에 상품 이름과 함께 넣고, 누르면 주문 상세로 간다")
    void autoConfirmed() {
        given(productNameService.findName(9L)).willReturn(Optional.of("곰돌이 티셔츠"));

        autoConfirmInboxService.addAutoConfirmed(command("AUTO"));

        var info = captured();
        assertThat(info.recipient()).isEqualTo(InboxRecipient.member(1L));
        assertThat(info.type()).isEqualTo(NotificationType.PURCHASE_AUTO_CONFIRMED);
        assertThat(info.title()).isEqualTo("구매가 자동으로 확정됐어요");
        assertThat(info.body()).isEqualTo("곰돌이 티셔츠 2개가 자동으로 구매확정됐어요.");
        assertThat(info.linkPath()).isEqualTo("/orders/3");
        assertThat(info.dedupKey()).isEqualTo("order-item.auto-confirmed:7");
    }

    @Test
    @DisplayName("상품 이름 복사본이 없으면 이름 없이 넣는다")
    void autoConfirmedWithoutName() {
        given(productNameService.findName(9L)).willReturn(Optional.empty());

        autoConfirmInboxService.addAutoConfirmed(command("AUTO"));

        assertThat(captured().body()).isEqualTo("주문상품 2개가 자동으로 구매확정됐어요.");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = "DIRECT")
    @DisplayName("직접 확정 · 확정 방식이 없는 예전 메시지는 구매자 알림함에 넣지 않는다")
    void notAutoConfirmed(String confirmType) {
        autoConfirmInboxService.addAutoConfirmed(command(confirmType));

        verifyNoInteractions(inboxNotificationService, productNameService);
    }

    private static PurchaseConfirmedCommand command(String confirmType) {
        return new PurchaseConfirmedCommand(7L, 3L, 1L, 5L, 9L, 2, confirmType);
    }

    private InboxNotificationInfo captured() {
        var captor = ArgumentCaptor.forClass(InboxNotificationInfo.class);
        verify(inboxNotificationService).add(captor.capture());
        return captor.getValue();
    }
}
