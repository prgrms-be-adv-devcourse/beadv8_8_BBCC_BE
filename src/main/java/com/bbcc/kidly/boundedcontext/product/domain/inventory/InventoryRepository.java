package com.bbcc.kidly.boundedcontext.product.domain.inventory;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface InventoryRepository {

    // 재고 행과 재고 조정 기록을 함께 저장한다. 옵션은 이미 저장돼 있어야 한다
    void saveOpening(InventoryOpening opening);

    // 판매 가능 수량이 qty 이상일 때만 줄이고, 줄였으면 true를 돌려준다 (INV-01). 재고 행이 없는 옵션이어도 false다
    // qty는 1 이상이어야 한다. 0 이하를 넘기면 재고가 늘거나, 아무것도 줄지 않았는데 true가 된다
    // 줄인 뒤에는 영속성 컨텍스트를 비우므로, 그 전에 읽은 엔티티는 다시 읽어야 한다
    boolean deductIfAvailable(long optionId, int qty, LocalDateTime now);

    // 옵션들의 재고 행을 한 번에 읽는다. 재고 행이 없는 옵션은 결과에 없다
    List<Inventory> findAllOfOptions(Collection<Long> optionIds);
}
