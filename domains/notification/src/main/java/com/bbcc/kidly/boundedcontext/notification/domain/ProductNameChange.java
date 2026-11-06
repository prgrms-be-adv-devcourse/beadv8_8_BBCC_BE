package com.bbcc.kidly.boundedcontext.notification.domain;

import java.util.Set;

/**
 * 상품 이름 복사본을 갱신하며 알게 된, 판매자에게 알릴 변화.
 *
 * @param newlyStoppedByAdmin 판매 중이던 상품을 관리자가 중지했으면 true
 * @param newlySoldOutOptionIds 품절이 아니던 옵션 중 새로 품절된 옵션 ID
 */
public record ProductNameChange(boolean newlyStoppedByAdmin, Set<Long> newlySoldOutOptionIds) {

    public static final ProductNameChange NONE = new ProductNameChange(false, Set.of());
}
