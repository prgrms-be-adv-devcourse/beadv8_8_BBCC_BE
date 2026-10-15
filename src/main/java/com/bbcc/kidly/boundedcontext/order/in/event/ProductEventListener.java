package com.bbcc.kidly.boundedcontext.order.in.event;

import com.bbcc.kidly.boundedcontext.order.app.replica.ReplicaService;
import com.bbcc.kidly.boundedcontext.order.app.replica.dto.ProductChangedCommand;
import com.bbcc.kidly.global.eventpublisher.DomainEventListener;
import com.bbcc.kidly.shared.product.event.ProductChangedEvent;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * product.changed를 받아 복사본 반영을 맡긴다. 이벤트 필드가 바뀌면 여기 변환만 고친다.
 * 리스너는 재시도가 없어 저장이 실패하면 이벤트가 사라지므로, 깨진 값은 여기서 걸러 예외 없이 처리한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductEventListener {

    /**
     * 판매 상태가 비었을 때 넣는 값. ON_SALE이 아니므로 구매 불가가 된다.
     */
    private static final String UNKNOWN_STATUS = "UNKNOWN";

    private final ReplicaService replicaService;

    /**
     * 복사본 행을 만들 수 없는 값(데이터 · 상품 ID · 판매자 ID · 상품명 · 발생 시각)이 없으면 이벤트 전체를 건너뛴다.
     */
    @DomainEventListener
    public void onProductChanged(ProductChangedEvent event) {
        if (EventGuard.accept("상품", event, "productId", productIdOf(event), missingRequiredValue(event))) {
            replicaService.applyProductChanged(toCommand(event));
        }
    }

    private static String missingRequiredValue(ProductChangedEvent event) {
        ProductChangedEvent.Data data = event.data();
        if (data == null) {
            return "missing-data";
        }
        if (data.productId() == null) {
            return "missing-product-id";
        }
        if (data.sellerId() == null) {
            return "missing-seller-id";
        }
        if (data.name() == null) {
            return "missing-product-name";
        }
        if (event.occurredAt() == null) {
            return "missing-occurred-at";
        }
        return null;
    }

    private static Long productIdOf(ProductChangedEvent event) {
        return event.data() == null ? null : event.data().productId();
    }

    /**
     * 상품 · 옵션 판매 상태는 enum 이름 문자열로, 옵션 가격은 단가로 옮긴다(in은 주문 domain을 쓰지 않음).
     * 판매 상태가 비면 UNKNOWN(구매 불가)으로 넘긴다.
     */
    private ProductChangedCommand toCommand(ProductChangedEvent event) {
        ProductChangedEvent.Data data = event.data();
        return new ProductChangedCommand(
            data.productId(),
            data.sellerId(),
            data.name(),
            data.categoryId(),
            data.status() == null ? UNKNOWN_STATUS : data.status().name(),
            toOptions(event.eventType(), data),
            event.occurredAt());
    }

    /**
     * 옵션 목록이 비면(null) 빈 목록, 옵션 ID가 없는 옵션은 건너뛰고, 같은 옵션 ID는 마지막 값 하나를 처음 자리에 둔다.
     * 건너뜀 · 중복은 상품 쪽 계약 위반이라 운영에서 보이게 WARN으로 남긴다.
     */
    private static List<ProductChangedCommand.Option> toOptions(String eventType, ProductChangedEvent.Data data) {
        if (data.options() == null) {
            return List.of();
        }
        Map<Long, ProductChangedCommand.Option> byOptionId = new LinkedHashMap<>();
        for (ProductChangedEvent.Option option : data.options()) {
            if (option == null || option.optionId() == null) {
                log.warn("상품 옵션 무시: event={} productId={} reason=missing-option-id", eventType, data.productId());
                continue;
            }
            if (byOptionId.put(option.optionId(), toOption(option)) != null) {
                log.warn("상품 옵션 중복: event={} productId={} optionId={} reason=duplicate-option-id",
                    eventType, data.productId(), option.optionId());
            }
        }
        return List.copyOf(byOptionId.values());
    }

    private static ProductChangedCommand.Option toOption(ProductChangedEvent.Option option) {
        return new ProductChangedCommand.Option(
            option.optionId(), option.optionName(), option.size(), option.price(), option.soldOut(),
            option.saleStatus() == null ? null : option.saleStatus().name());
    }
}
