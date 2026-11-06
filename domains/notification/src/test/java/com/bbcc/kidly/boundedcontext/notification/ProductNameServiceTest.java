package com.bbcc.kidly.boundedcontext.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.bbcc.kidly.boundedcontext.notification.app.InboxNotificationService;
import com.bbcc.kidly.boundedcontext.notification.app.NotificationService;
import com.bbcc.kidly.boundedcontext.notification.app.ProductNameService;
import com.bbcc.kidly.boundedcontext.notification.app.dto.ProductChangedCommand;
import com.bbcc.kidly.boundedcontext.notification.app.dto.ProductChangedCommand.Option;
import com.bbcc.kidly.boundedcontext.notification.domain.InboxNotificationInfo;
import com.bbcc.kidly.boundedcontext.notification.domain.NotificationType;
import com.bbcc.kidly.boundedcontext.notification.domain.ProductName;
import com.bbcc.kidly.boundedcontext.notification.domain.ProductNameRepository;
import com.bbcc.kidly.shared.product.domain.ProductStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductNameServiceTest {

    private static final UUID EVENT_ID = UUID.randomUUID();
    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 11, 5, 9, 0);
    private static final List<Option> IN_STOCK = List.of(
        new Option(11L, "색상: 네이비 / 사이즈: 110", false), new Option(12L, "색상: 네이비 / 사이즈: 120", false));

    @Mock
    private ProductNameRepository productNameRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private InboxNotificationService inboxNotificationService;

    @InjectMocks
    private ProductNameService productNameService;

    @Test
    @DisplayName("처음 받은 상품은 관리자 판매 중지 · 품절이어도 복사본만 만들고 알리지 않는다")
    void firstReceived() {
        given(productNameRepository.findById(1L)).willReturn(Optional.empty());

        productNameService.save(command(true, List.of(new Option(11L, "네이비", true)), OCCURRED_AT));

        verify(productNameRepository).save(any());
        verifyNoInteractions(notificationService, inboxNotificationService);
    }

    @Test
    @DisplayName("관리자가 새로 판매를 중지하면 판매자 메일 · 알림함을 만든다")
    void stoppedByAdmin() {
        givenCopy();
        var command = command(true, IN_STOCK, OCCURRED_AT.plusSeconds(1));

        productNameService.save(command);

        verify(notificationService).createProductStoppedByAdminEmail(command);
        verifyNoInteractions(inboxNotificationService);
    }

    @Test
    @DisplayName("재고가 있던 옵션이 함께 품절되면 판매자 알림함에 한 건으로 묶어 넣는다")
    void soldOut() {
        givenCopy();

        productNameService.save(command(false, List.of(
            new Option(11L, "색상: 네이비 / 사이즈: 110", true), new Option(12L, "색상: 네이비 / 사이즈: 120", true)),
            OCCURRED_AT.plusSeconds(1)));

        var inbox = ArgumentCaptor.forClass(InboxNotificationInfo.class);
        verify(inboxNotificationService).add(inbox.capture());
        assertThat(inbox.getValue().type()).isEqualTo(NotificationType.PRODUCT_OPTION_SOLD_OUT);
        assertThat(inbox.getValue().linkPath()).isEqualTo("/center/products/1");
        assertThat(inbox.getValue().dedupKey()).isEqualTo("product.changed:" + EVENT_ID + ":sold-out");
        assertThat(inbox.getValue().body()).contains("색상: 네이비 / 사이즈: 110");
        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("판매 상태 · 품절이 그대로면 알리지 않는다")
    void unchanged() {
        givenCopy();

        productNameService.save(command(false, IN_STOCK, OCCURRED_AT.plusSeconds(1)));

        verifyNoInteractions(notificationService, inboxNotificationService);
    }

    @Test
    @DisplayName("늦게 온 옛 메시지는 관리자 판매 중지 · 품절이어도 알리지 않는다")
    void ignoreOlder() {
        givenCopy();

        productNameService.save(command(true, List.of(new Option(11L, "네이비", true)), OCCURRED_AT.minusSeconds(1)));

        verifyNoInteractions(notificationService, inboxNotificationService);
    }

    @Test
    @DisplayName("같은 메시지를 다시 받으면 알리지 않는다")
    void sameMessageAgain() {
        givenCopy();
        var command = command(true, List.of(new Option(11L, "네이비", true)), OCCURRED_AT.plusSeconds(1));

        productNameService.save(command);
        productNameService.save(command);

        verify(notificationService).createProductStoppedByAdminEmail(command);
        verify(inboxNotificationService).add(any());
    }

    private void givenCopy() {
        var copy = ProductName.create(command(false, IN_STOCK, OCCURRED_AT).toInfo());
        given(productNameRepository.findById(1L)).willReturn(Optional.of(copy));
    }

    private static ProductChangedCommand command(boolean stoppedByAdmin, List<Option> options,
        LocalDateTime occurredAt) {
        return new ProductChangedCommand(EVENT_ID, 1L, 7L, "곰돌이 티셔츠",
            stoppedByAdmin ? ProductStatus.STOPPED : ProductStatus.ON_SALE, stoppedByAdmin, options, occurredAt);
    }
}
