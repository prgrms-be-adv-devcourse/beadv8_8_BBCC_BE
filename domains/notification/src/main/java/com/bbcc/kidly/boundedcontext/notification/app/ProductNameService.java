package com.bbcc.kidly.boundedcontext.notification.app;

import com.bbcc.kidly.boundedcontext.notification.app.dto.ProductChangedCommand;
import com.bbcc.kidly.boundedcontext.notification.domain.InboxNotificationInfo;
import com.bbcc.kidly.boundedcontext.notification.domain.NotificationType;
import com.bbcc.kidly.boundedcontext.notification.domain.ProductName;
import com.bbcc.kidly.boundedcontext.notification.domain.ProductNameChange;
import com.bbcc.kidly.boundedcontext.notification.domain.ProductNameRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 알림 문구에 쓸 상품 이름 복사본을 저장하고 찾는다.
 * 복사본보다 늦은 변경에서 판매 중이던 상품을 관리자가 중지했으면 판매자에게 메일 · 알림함을, 재고가 있던 옵션이 새로 품절됐으면
 * 알림함만 남긴다(품절은 다음에 들어와서 알면 되는 일). 처음 받은 상품은 이전 상태를 몰라 알리지 않는다.
 * 판매 중이던 상품을 관리자가 멈춘 경우만 판매 중지를 알린다. 품절은 주문 차감과 판매자 재고 조정 모두 알린다.
 * 같은 상품의 이벤트는 같은 파티션(키 = 상품 ID)으로 한 건씩 오므로 같은 행을 동시에 만들지 않는다.
 */
@Service
@RequiredArgsConstructor
public class ProductNameService {

    private final ProductNameRepository productNameRepository;
    private final NotificationService notificationService;
    private final InboxNotificationService inboxNotificationService;

    @Transactional
    public void save(ProductChangedCommand command) {
        var info = command.toInfo();
        var change = productNameRepository.findById(command.productId())
            .map(productName -> productName.applyIfNewer(info))
            .orElseGet(() -> {
                productNameRepository.save(ProductName.create(info));
                return ProductNameChange.NONE;
            });
        if (change.newlyStoppedByAdmin()) {
            notificationService.createProductStoppedByAdminEmail(command);
        }
        if (!change.newlySoldOutOptionIds().isEmpty()) {
            var content = ProductSoldOutInbox.compose(command.name(),
                command.optionNames(change.newlySoldOutOptionIds()));
            inboxNotificationService.add(new InboxNotificationInfo(command.sellerId(),
                NotificationType.PRODUCT_OPTION_SOLD_OUT, content.title(), content.body(),
                "/center/products/" + command.productId(),
                "product.changed:" + command.eventId() + ":sold-out"));
        }
    }

    // 못 찾으면(아직 상품 이벤트를 받지 못했다) 부르는 쪽이 이름 없는 문구를 쓴다
    @Transactional(readOnly = true)
    public Optional<String> findName(Long productId) {
        return productNameRepository.findById(productId).map(ProductName::getName);
    }
}
