package com.bbcc.kidly.boundedcontext.product.out.inventory;

import com.bbcc.kidly.boundedcontext.product.domain.inventory.Inventory;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryRepository;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaInventoryRepository extends JpaRepository<Inventory, Long>, InventoryRepository {

    @Override
    default void restore(long optionId, int qty, LocalDateTime now) {
        if (increaseAvailableQty(optionId, qty, now) != 1) {
            throw new IllegalStateException("옵션 " + optionId + "의 재고 행이 없어 복구할 수 없습니다");
        }
    }

    // JPA Auditing이 동작하지 않는 벌크 UPDATE라서 updatedAt을 쿼리에서 직접 넣는다
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        UPDATE Inventory i
        SET i.availableQty = i.availableQty + :qty, i.updatedAt = :now
        WHERE i.productOption.id = :optionId
        """)
    int increaseAvailableQty(@Param("optionId") long optionId, @Param("qty") int qty, @Param("now") LocalDateTime now);

    @Override
    default boolean deductIfAvailable(long optionId, int qty, LocalDateTime now) {
        return deduct(optionId, qty, now) == 1;
    }

    // JPA Auditing이 동작하지 않는 벌크 UPDATE라서 updatedAt을 쿼리에서 직접 넣는다
    // 먼저 쓰지 않으면 비울 때 아직 쓰지 않은 변경이 함께 사라진다
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        UPDATE Inventory i
        SET i.availableQty = i.availableQty - :qty, i.updatedAt = :now
        WHERE i.productOption.id = :optionId AND i.availableQty >= :qty
        """)
    int deduct(@Param("optionId") long optionId, @Param("qty") int qty, @Param("now") LocalDateTime now);

    @Override
    default boolean adjustIfAvailable(long optionId, int deltaQty, LocalDateTime now) {
        return adjust(optionId, deltaQty, now) == 1;
    }

    // JPA Auditing이 동작하지 않는 벌크 UPDATE라서 updatedAt을 쿼리에서 직접 넣는다
    // 먼저 쓰지 않으면 비울 때 아직 쓰지 않은 변경이 함께 사라진다
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        UPDATE Inventory i
        SET i.availableQty = i.availableQty + :deltaQty, i.updatedAt = :now
        WHERE i.productOption.id = :optionId AND i.availableQty + :deltaQty >= 0
        """)
    int adjust(@Param("optionId") long optionId, @Param("deltaQty") int deltaQty, @Param("now") LocalDateTime now);

    @Query("SELECT i FROM Inventory i WHERE i.productOption.id IN :optionIds")
    List<Inventory> findAllOfOptions(@Param("optionIds") Collection<Long> optionIds);

    @Override
    default Map<Long, Integer> findAvailableQtyByOptionId(Collection<Long> optionIds) {
        Map<Long, Integer> availableQtyByOptionId = new HashMap<>();
        for (Inventory inventory : findAllOfOptions(optionIds)) {
            availableQtyByOptionId.put(inventory.getProductOptionId(), inventory.getAvailableQty());
        }
        return availableQtyByOptionId;
    }

    @Override
    default void deleteByOptionIds(Collection<Long> optionIds) {
        if (optionIds.isEmpty()) {
            return;
        }
        deleteAdjustmentRecordsOfOptions(optionIds);
        deleteInventoriesOfOptions(optionIds);
    }

    @Modifying
    @Query("DELETE FROM InventoryAdjustmentRecord r WHERE r.productOption.id IN :optionIds")
    void deleteAdjustmentRecordsOfOptions(@Param("optionIds") Collection<Long> optionIds);

    @Modifying
    @Query("DELETE FROM Inventory i WHERE i.productOption.id IN :optionIds")
    void deleteInventoriesOfOptions(@Param("optionIds") Collection<Long> optionIds);
}
