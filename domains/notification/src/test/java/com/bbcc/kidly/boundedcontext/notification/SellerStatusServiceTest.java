package com.bbcc.kidly.boundedcontext.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.bbcc.kidly.boundedcontext.notification.app.MemberContactService;
import com.bbcc.kidly.boundedcontext.notification.app.NotificationService;
import com.bbcc.kidly.boundedcontext.notification.app.SellerStatusService;
import com.bbcc.kidly.boundedcontext.notification.app.dto.SellerChangedCommand;
import com.bbcc.kidly.boundedcontext.notification.domain.SellerStatusCopy;
import com.bbcc.kidly.boundedcontext.notification.domain.SellerStatusCopyRepository;
import com.bbcc.kidly.shared.seller.domain.SellerStatus;
import java.time.LocalDateTime;
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
class SellerStatusServiceTest {

    private static final UUID EVENT_ID = UUID.randomUUID();
    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 11, 5, 9, 0);

    @Mock
    private SellerStatusCopyRepository sellerStatusCopyRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private MemberContactService memberContactService;

    @InjectMocks
    private SellerStatusService sellerStatusService;

    @Test
    @DisplayName("복사본이 없는 판매자의 ACTIVE는 승인이므로 복사본을 만들고 승인 메일을 만든다")
    void approved() {
        given(sellerStatusCopyRepository.findById(5L)).willReturn(Optional.empty());
        given(memberContactService.findEmail(5L)).willReturn(Optional.of("seller@kidly.dev"));

        sellerStatusService.apply(command(SellerStatus.ACTIVE, OCCURRED_AT));

        var saved = ArgumentCaptor.forClass(SellerStatusCopy.class);
        verify(sellerStatusCopyRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(SellerStatus.ACTIVE);
        verify(notificationService).createSellerApprovedEmail(EVENT_ID, 5L, "키들리 상점");
    }

    @Test
    @DisplayName("승인을 받았는데 회원 이메일 복사본이 아직 없으면 복사본을 저장하지 않고 실패해 다시 받을 수 있게 한다")
    void approvedWithoutEmail() {
        given(sellerStatusCopyRepository.findById(5L)).willReturn(Optional.empty());
        given(memberContactService.findEmail(5L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> sellerStatusService.apply(command(SellerStatus.ACTIVE, OCCURRED_AT)))
            .isInstanceOf(IllegalStateException.class);

        verify(sellerStatusCopyRepository, never()).save(any());
        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("ACTIVE에서 SUSPENDED로 바뀌면 정지 메일을 만든다")
    void suspended() {
        givenCopy(SellerStatus.ACTIVE);

        sellerStatusService.apply(command(SellerStatus.SUSPENDED, OCCURRED_AT.plusSeconds(1)));

        verify(notificationService).createSellerSuspendedEmail(EVENT_ID, 5L, "키들리 상점");
    }

    @Test
    @DisplayName("상호명만 바뀌어 상태가 같으면 메일을 만들지 않는다")
    void businessNameOnly() {
        givenCopy(SellerStatus.ACTIVE);

        sellerStatusService.apply(command(SellerStatus.ACTIVE, OCCURRED_AT.plusSeconds(1)));

        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("늦게 온 옛 이벤트는 상태가 달라도 메일을 만들지 않는다")
    void ignoreOlder() {
        givenCopy(SellerStatus.ACTIVE);

        sellerStatusService.apply(command(SellerStatus.SUSPENDED, OCCURRED_AT.minusSeconds(1)));

        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("복사본이 없는 판매자의 SUSPENDED는 언제 정지됐는지 몰라 복사본만 만들고 메일은 만들지 않는다")
    void firstSuspended() {
        given(sellerStatusCopyRepository.findById(anyLong())).willReturn(Optional.empty());

        sellerStatusService.apply(command(SellerStatus.SUSPENDED, OCCURRED_AT));

        verify(sellerStatusCopyRepository).save(any());
        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("ACTIVE에서 CLOSED(탈퇴)로 바뀌면 복사본만 CLOSED로 바꾸고 메일을 만들지 않는다")
    void closedFromActive() {
        var copy = givenCopy(SellerStatus.ACTIVE);

        sellerStatusService.apply(command(SellerStatus.CLOSED, OCCURRED_AT.plusSeconds(1)));

        assertThat(copy.getStatus()).isEqualTo(SellerStatus.CLOSED);
        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("SUSPENDED에서 CLOSED(탈퇴)로 바뀌면 복사본만 CLOSED로 바꾸고 메일을 만들지 않는다")
    void closedFromSuspended() {
        var copy = givenCopy(SellerStatus.SUSPENDED);

        sellerStatusService.apply(command(SellerStatus.CLOSED, OCCURRED_AT.plusSeconds(1)));

        assertThat(copy.getStatus()).isEqualTo(SellerStatus.CLOSED);
        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("복사본이 없는 판매자의 CLOSED는 복사본만 CLOSED로 만들고 메일을 만들지 않는다")
    void firstClosed() {
        given(sellerStatusCopyRepository.findById(5L)).willReturn(Optional.empty());

        sellerStatusService.apply(command(SellerStatus.CLOSED, OCCURRED_AT));

        var saved = ArgumentCaptor.forClass(SellerStatusCopy.class);
        verify(sellerStatusCopyRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(SellerStatus.CLOSED);
        verifyNoInteractions(notificationService, memberContactService);
    }

    private SellerStatusCopy givenCopy(SellerStatus status) {
        var copy = SellerStatusCopy.create(5L, status, OCCURRED_AT);
        given(sellerStatusCopyRepository.findById(5L)).willReturn(Optional.of(copy));
        return copy;
    }

    private static SellerChangedCommand command(SellerStatus status, LocalDateTime occurredAt) {
        return new SellerChangedCommand(EVENT_ID, 5L, "키들리 상점", status, occurredAt);
    }
}
