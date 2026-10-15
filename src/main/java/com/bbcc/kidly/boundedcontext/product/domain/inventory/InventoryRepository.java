package com.bbcc.kidly.boundedcontext.product.domain.inventory;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Map;

public interface InventoryRepository {

    // 재고 행과 재고 조정 기록을 함께 저장한다. 옵션은 이미 저장돼 있어야 한다
    void saveOpening(InventoryOpening opening);

    // 판매 가능 수량을 qty만큼 늘린다. 재고 행이 없는 옵션이면 예외를 던진다
    // 늘린 뒤에는 영속성 컨텍스트를 비우므로, 그 전에 읽은 엔티티는 다시 읽어야 한다
    void restore(long optionId, int qty, LocalDateTime now);

    // 판매 가능 수량이 qty 이상일 때만 줄이고, 줄였으면 true를 돌려준다 (INV-01). 재고 행이 없는 옵션이어도 false다
    // qty는 1 이상이어야 한다. 0 이하를 넘기면 재고가 늘거나, 아무것도 줄지 않았는데 true가 된다
    // 줄인 뒤에는 영속성 컨텍스트를 비우므로, 그 전에 읽은 엔티티는 다시 읽어야 한다
    boolean deductIfAvailable(long optionId, int qty, LocalDateTime now);

    // 판매 가능 수량에 증감량을 더한 결과가 0 이상일 때만 바꾸고, 바꿨으면 true를 돌려준다
    // 판매자는 판매 가능 수량까지만 줄일 수 있다 (INV-03). 재고 행이 없는 옵션이어도 false다
    // 바꾼 뒤에는 영속성 컨텍스트를 비우므로, 그 전에 읽은 엔티티는 다시 읽어야 한다
    boolean adjustIfAvailable(long optionId, int deltaQty, LocalDateTime now);

    // 판매자가 조정한 재고 조정 기록을 저장한다. 수량을 바꾼 같은 트랜잭션에서 불러야 한다
    void saveAdjustmentRecord(InventoryAdjustmentRecord record);

    // 옵션 ID별 판매 가능 수량을 한 번에 읽는다. 재고 행이 없는 옵션은 결과에 없다
    Map<Long, Integer> findAvailableQtyByOptionId(Collection<Long> optionIds);

    // 판매대기 상품에서 뺀 옵션의 재고 행과 재고 조정 기록을 지운다. 판매대기 상품은 주문이 없어 차감 기록이 없다.
    // 옵션 ID가 비어 있으면 아무것도 하지 않는다.
    // 옵션을 지우는 변경을 flush하기 전에 불러야 한다. 옵션이 먼저 지워지면 재고 행이 옵션을 가리켜 막힌다
    void deleteByOptionIds(Collection<Long> optionIds);
}
