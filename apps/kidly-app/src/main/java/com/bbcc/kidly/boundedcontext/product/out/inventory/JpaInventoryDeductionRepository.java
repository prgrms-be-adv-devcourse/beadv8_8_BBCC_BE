package com.bbcc.kidly.boundedcontext.product.out.inventory;

import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryDeduction;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryDeductionRepository;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaInventoryDeductionRepository
        extends JpaRepository<InventoryDeduction, Long>, InventoryDeductionRepository {

    @Override
    default boolean restoreIfDeducted(long deductionId, LocalDateTime now) {
        return markRestored(deductionId, now) == 1;
    }

    // 상태 확인과 변경을 UPDATE 한 문장에서 함께 해서, 같은 복구를 동시에 해도 한 쪽만 상태를 바꾼다 (INV-04)
    // JPA Auditing이 동작하지 않는 벌크 UPDATE라서 updatedAt을 쿼리에서 직접 넣는다
    // 쿼리 전에 같은 트랜잭션의 변경을 DB에 먼저 쓰고, 쿼리 뒤에 영속성 컨텍스트를 비워 바뀌기 전 상태가 남지 않게 한다
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        UPDATE InventoryDeduction d
        SET d.status = com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryDeductionStatus.RESTORED,
            d.updatedAt = :now
        WHERE d.id = :id
          AND d.status = com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryDeductionStatus.DEDUCTED
        """)
    int markRestored(@Param("id") long id, @Param("now") LocalDateTime now);
}
