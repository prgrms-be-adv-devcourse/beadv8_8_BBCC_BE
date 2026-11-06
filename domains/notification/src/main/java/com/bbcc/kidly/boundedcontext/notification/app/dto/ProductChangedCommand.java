package com.bbcc.kidly.boundedcontext.notification.app.dto;

import com.bbcc.kidly.boundedcontext.notification.domain.ProductNameInfo;
import com.bbcc.kidly.shared.product.domain.ProductStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 상품 이름 복사본을 갱신하고 판매자에게 관리자 판매 중지 · 옵션 품절을 알릴 상품 변경.
 *
 * @param eventId 상품 변경 이벤트 ID. 같은 이벤트로는 한 건만 만든다
 * @param sellerId 판매자 ID. 회원 ID와 같다
 * @param stoppedByAdmin 관리자가 판매를 중지한 상품이면 true
 * @param options 상품의 옵션. 보관한 옵션은 없다
 * @param occurredAt 이벤트 발생 시각. 복사본보다 늦을 때만 반영한다
 */
public record ProductChangedCommand(UUID eventId, Long productId, Long sellerId, String name, ProductStatus status,
    boolean stoppedByAdmin, List<Option> options, LocalDateTime occurredAt) {

    public record Option(Long optionId, String optionName, boolean soldOut) {
    }

    public ProductNameInfo toInfo() {
        var inStock = options.stream().filter(option -> !option.soldOut()).map(Option::optionId)
            .collect(Collectors.toSet());
        var soldOut = options.stream().filter(Option::soldOut).map(Option::optionId).collect(Collectors.toSet());
        return new ProductNameInfo(productId, name, status, stoppedByAdmin, inStock, soldOut, occurredAt);
    }

    public List<String> optionNames(Set<Long> optionIds) {
        return options.stream().filter(option -> optionIds.contains(option.optionId()))
            .map(Option::optionName).toList();
    }
}
