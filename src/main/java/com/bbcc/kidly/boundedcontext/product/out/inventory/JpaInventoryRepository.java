package com.bbcc.kidly.boundedcontext.product.out.inventory;

import com.bbcc.kidly.boundedcontext.product.domain.inventory.Inventory;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryRepository;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaInventoryRepository extends JpaRepository<Inventory, Long>, InventoryRepository {

    @Override
    default boolean deductIfAvailable(long optionId, int qty, LocalDateTime now) {
        return deduct(optionId, qty, now) == 1;
    }

    // 판단과 차감을 UPDATE 한 문장에서 함께 해서, 동시에 차감해도 판매 가능 수량이 0 아래로 내려가지 않는다 (INV-01)
    // JPA Auditing이 동작하지 않는 벌크 UPDATE라서 updatedAt을 쿼리에서 직접 넣는다
    // 쿼리 전에 같은 트랜잭션의 변경을 DB에 먼저 쓰고, 쿼리 뒤에 영속성 컨텍스트를 비워 줄기 전 재고가 남지 않게 한다.
    // 먼저 쓰지 않으면 비울 때 아직 쓰지 않은 변경이 함께 사라진다
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        UPDATE Inventory i
        SET i.availableQty = i.availableQty - :qty, i.updatedAt = :now
        WHERE i.productOption.id = :optionId AND i.availableQty >= :qty
        """)
    int deduct(@Param("optionId") long optionId, @Param("qty") int qty, @Param("now") LocalDateTime now);
}
