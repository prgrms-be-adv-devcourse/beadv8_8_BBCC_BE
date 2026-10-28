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
    // 쿼리 전에 같은 트랜잭션의 변경을 DB에 먼저 쓰고, 쿼리 뒤에 영속성 컨텍스트를 비워 늘기 전 재고가 남지 않게 한다
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

    @Override
    default boolean adjustIfAvailable(long optionId, int deltaQty, LocalDateTime now) {
        return adjust(optionId, deltaQty, now) == 1;
    }

    // 판단과 변경을 UPDATE 한 문장에서 함께 해서, 동시에 줄여도 판매 가능 수량이 0 아래로 내려가지 않는다 (INV-03)
    // JPA Auditing이 동작하지 않는 벌크 UPDATE라서 updatedAt을 쿼리에서 직접 넣는다
    // 쿼리 전에 같은 트랜잭션의 변경을 DB에 먼저 쓰고, 쿼리 뒤에 영속성 컨텍스트를 비워 바뀌기 전 재고가 남지 않게 한다.
    // 먼저 쓰지 않으면 비울 때 아직 쓰지 않은 변경이 함께 사라진다
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        UPDATE Inventory i
        SET i.availableQty = i.availableQty + :deltaQty, i.updatedAt = :now
        WHERE i.productOption.id = :optionId AND i.availableQty + :deltaQty >= 0
        """)
    int adjust(@Param("optionId") long optionId, @Param("deltaQty") int deltaQty, @Param("now") LocalDateTime now);

    // 옵션 ID는 지연 로딩 연관 옵션의 id로 비교하므로 옵션을 읽지 않는다
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

    // 조정 기록을 먼저 지우고 재고 행을 지운다. 쿼리 전에 영속성 컨텍스트를 먼저 쓰지 않고 쿼리 뒤에도 비우지 않는다.
    // 호출한 쪽이 뺀 옵션의 삭제를 아직 쓰지 않았을 때 먼저 쓰면, 재고 행이 남은 채 옵션이 지워져 외래키 오류가 나기 때문이다
    @Modifying
    @Query("DELETE FROM InventoryAdjustmentRecord r WHERE r.productOption.id IN :optionIds")
    void deleteAdjustmentRecordsOfOptions(@Param("optionIds") Collection<Long> optionIds);

    @Modifying
    @Query("DELETE FROM Inventory i WHERE i.productOption.id IN :optionIds")
    void deleteInventoriesOfOptions(@Param("optionIds") Collection<Long> optionIds);
}
