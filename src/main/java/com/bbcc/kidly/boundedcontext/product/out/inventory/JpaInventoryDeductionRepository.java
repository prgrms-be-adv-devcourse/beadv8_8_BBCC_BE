package com.bbcc.kidly.boundedcontext.product.out.inventory;

import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryDeduction;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryDeductionRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaInventoryDeductionRepository
        extends JpaRepository<InventoryDeduction, Long>, InventoryDeductionRepository {
}
