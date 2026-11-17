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

    // JPA Auditing이 동작하지 않는 벌크 UPDATE라서 updatedAt을 쿼리에서 직접 넣는다
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
