package com.bbcc.kidly.boundedcontext.notification.domain;

import com.bbcc.kidly.shared.product.domain.ProductStatus;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * @param soldOutOptionIds 품절인 옵션 ID. 보관해 이벤트에서 빠진 옵션은 어느 쪽에도 없다
 * @param occurredAt 이벤트 발생 시각. 복사본보다 늦을 때만 반영한다
 */
public record ProductNameInfo(Long productId, String name, ProductStatus status, boolean stoppedByAdmin,
    Set<Long> inStockOptionIds, Set<Long> soldOutOptionIds, LocalDateTime occurredAt) {
}
