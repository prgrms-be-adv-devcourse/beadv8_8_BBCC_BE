package com.bbcc.kidly.boundedcontext.notification.app;

import com.bbcc.kidly.boundedcontext.notification.app.dto.SellerChangedCommand;
import com.bbcc.kidly.boundedcontext.notification.domain.SellerStatusCopy;
import com.bbcc.kidly.boundedcontext.notification.domain.SellerStatusCopyRepository;
import com.bbcc.kidly.shared.seller.domain.SellerStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 판매자 상태 복사본을 갱신하고, 상태가 바뀐 때만 판매자에게 메일을 만든다.
 * 처음 받은 ACTIVE는 승인, ACTIVE에서 SUSPENDED로 바뀐 것은 정지다. 정지 해제는 없다(SellerStatus).
 * 처음 받은 SUSPENDED는 언제 정지됐는지 알 수 없어 메일을 만들지 않는다. 상호명만 바뀐 것 · 같은 상태도 만들지 않는다.
 * 같은 판매자의 이벤트는 같은 파티션(키 = 판매자 ID)으로 한 건씩 오므로 같은 행을 동시에 만들지 않는다.
 */
@Service
@RequiredArgsConstructor
public class SellerStatusService {

    private final SellerStatusCopyRepository sellerStatusCopyRepository;
    private final NotificationService notificationService;

    @Transactional
    public void apply(SellerChangedCommand command) {
        sellerStatusCopyRepository.findById(command.sellerId()).ifPresentOrElse(
            copy -> {
                if (copy.changeIfNewer(command.status(), command.occurredAt())
                    && command.status() == SellerStatus.SUSPENDED) {
                    notificationService.createSellerSuspendedEmail(command.eventId(), command.sellerId(),
                        command.businessName());
                }
            },
            () -> {
                sellerStatusCopyRepository.save(
                    SellerStatusCopy.create(command.sellerId(), command.status(), command.occurredAt()));
                if (command.status() == SellerStatus.ACTIVE) {
                    notificationService.createSellerApprovedEmail(command.eventId(), command.sellerId(),
                        command.businessName());
                }
            });
    }
}
