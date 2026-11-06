package com.bbcc.kidly.boundedcontext.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.notification.app.InboxNotificationService;
import com.bbcc.kidly.boundedcontext.notification.app.ProductNameService;
import com.bbcc.kidly.boundedcontext.notification.app.dto.InboxNotificationView;
import com.bbcc.kidly.boundedcontext.notification.app.dto.ProductChangedCommand;
import com.bbcc.kidly.boundedcontext.notification.domain.NotificationType;
import com.bbcc.kidly.shared.product.domain.ProductStatus;
import com.bbcc.kidly.support.IntegrationTest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class ProductNameServiceIntegrationTest {

    private static final Long PRODUCT_ID = 10L;
    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 11, 5, 9, 0);

    @Autowired
    private ProductNameService productNameService;

    @Autowired
    private InboxNotificationService inboxNotificationService;

    @Test
    @DisplayName("처음 받은 상품 이름을 저장하고 상품 ID로 찾는다")
    void saveAndFind() {
        productNameService.save(changed("곰돌이 티셔츠", OCCURRED_AT));

        assertThat(productNameService.findName(PRODUCT_ID)).contains("곰돌이 티셔츠");
    }

    @Test
    @DisplayName("더 늦게 발생한 이벤트면 덮어쓰고, 옛 이벤트는 무시한다")
    void overwriteOnlyNewer() {
        productNameService.save(changed("곰돌이 티셔츠", OCCURRED_AT));

        productNameService.save(changed("곰돌이 반팔 티셔츠", OCCURRED_AT.plusMinutes(1)));
        productNameService.save(changed("옛 이름", OCCURRED_AT.plusSeconds(30)));

        assertThat(productNameService.findName(PRODUCT_ID)).contains("곰돌이 반팔 티셔츠");
    }

    @Test
    @DisplayName("재고 옵션을 배열로 저장했다가 다시 읽어 관리자 판매 중지 · 품절을 알린다")
    void saveSaleState() {
        productNameService.save(new ProductChangedCommand(UUID.randomUUID(), PRODUCT_ID, 7L, "곰돌이 티셔츠",
            ProductStatus.ON_SALE, false,
            List.of(new ProductChangedCommand.Option(11L, "110", false),
                new ProductChangedCommand.Option(12L, "120", false)), OCCURRED_AT));

        productNameService.save(new ProductChangedCommand(UUID.randomUUID(), PRODUCT_ID, 7L, "곰돌이 티셔츠",
            ProductStatus.STOPPED, true,
            List.of(new ProductChangedCommand.Option(11L, "110", true),
                new ProductChangedCommand.Option(12L, "120", false)), OCCURRED_AT.plusMinutes(1)));

        assertThat(inboxNotificationService.findMine(7L, 0, 10).getContent())
            .extracting(InboxNotificationView::type)
            .containsExactlyInAnyOrder(NotificationType.PRODUCT_STOPPED_BY_ADMIN.name(),
                NotificationType.PRODUCT_OPTION_SOLD_OUT.name());
    }

    @Test
    @DisplayName("받지 못한 상품이면 비어 있다")
    void notFound() {
        assertThat(productNameService.findName(PRODUCT_ID)).isEmpty();
    }

    private static ProductChangedCommand changed(String name, LocalDateTime occurredAt) {
        return new ProductChangedCommand(UUID.randomUUID(), PRODUCT_ID, 7L, name, ProductStatus.ON_SALE, false,
            List.of(), occurredAt);
    }
}
