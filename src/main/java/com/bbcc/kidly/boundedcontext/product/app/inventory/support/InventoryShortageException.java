package com.bbcc.kidly.boundedcontext.product.app.inventory.support;

/**
 * 판매 가능 수량이 모자라 재고를 줄이지 못했다. InventoryDeductor가 던져 자기 트랜잭션을 롤백한다.
 */
public class InventoryShortageException extends RuntimeException {

    public InventoryShortageException(long optionId, int qty) {
        super("옵션 " + optionId + "의 판매 가능 수량이 " + qty + "보다 적습니다");
    }
}
