package com.bbcc.kidly.boundedcontext.product.app.inventory.support;

import java.util.Set;

/**
 * 판매 가능 수량이 모자라 재고를 줄이지 못했다. InventoryDeductor가 모자란 옵션을 모두 모아 던져 자기 트랜잭션을 롤백한다.
 */
public class InventoryShortageException extends RuntimeException {

    private final Set<Long> shortOptionIds;

    public InventoryShortageException(Set<Long> shortOptionIds) {
        super("옵션 " + shortOptionIds + "의 판매 가능 수량이 요청 수량보다 적습니다");
        this.shortOptionIds = Set.copyOf(shortOptionIds);
    }

    // 재고 부족함에 담을 거절 사유를 정하려고 모자란 옵션 ID를 꺼낸다
    public Set<Long> shortOptionIds() {
        return shortOptionIds;
    }
}
