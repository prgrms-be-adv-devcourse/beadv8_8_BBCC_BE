package com.bbcc.kidly.boundedcontext.product.app.inventory.support;

import java.util.Set;

public class InventoryShortageException extends RuntimeException {

    private final Set<Long> shortOptionIds;

    public InventoryShortageException(Set<Long> shortOptionIds) {
        super("옵션 " + shortOptionIds + "의 판매 가능 수량이 요청 수량보다 적습니다");
        this.shortOptionIds = Set.copyOf(shortOptionIds);
    }

    public Set<Long> shortOptionIds() {
        return shortOptionIds;
    }
}
