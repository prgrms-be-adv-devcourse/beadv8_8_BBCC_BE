package com.bbcc.kidly.boundedcontext.order.in.event;

import com.bbcc.kidly.boundedcontext.order.app.replica.ReplicaService;
import com.bbcc.kidly.boundedcontext.order.app.replica.dto.ProductChangedCommand;
import com.bbcc.kidly.global.eventpublisher.DomainEventListener;
import com.bbcc.kidly.shared.product.event.ProductChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * product.changed를 받아 복사본 반영을 맡긴다. 이벤트 필드가 바뀌면 여기 변환만 고친다.
 */
@Component
@RequiredArgsConstructor
public class ProductEventListener {

    private final ReplicaService replicaService;

    @DomainEventListener
    public void onProductChanged(ProductChangedEvent event) {
        replicaService.applyProductChanged(toCommand(event));
    }

    /**
     * 판매 상태는 enum 이름 문자열로, 옵션 가격은 단가로 옮긴다.
     */
    private ProductChangedCommand toCommand(ProductChangedEvent event) {
        ProductChangedEvent.Data data = event.data();
        return new ProductChangedCommand(
            data.productId(),
            data.sellerId(),
            data.name(),
            data.categoryId(),
            data.status().name(),
            data.options().stream()
                .map(option -> new ProductChangedCommand.Option(
                    option.optionId(), option.size(), option.price(), option.soldOut()))
                .toList(),
            event.occurredAt());
    }
}
