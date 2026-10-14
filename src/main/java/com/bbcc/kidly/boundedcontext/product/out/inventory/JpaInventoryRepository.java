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
    @Modifying(clearAutomatically = true)
    @Query("""
        UPDATE Inventory i
        SET i.availableQty = i.availableQty - :qty, i.updatedAt = :now
        WHERE i.productOption.id = :optionId AND i.availableQty >= :qty
        """)
    int deduct(@Param("optionId") long optionId, @Param("qty") int qty, @Param("now") LocalDateTime now);
}
