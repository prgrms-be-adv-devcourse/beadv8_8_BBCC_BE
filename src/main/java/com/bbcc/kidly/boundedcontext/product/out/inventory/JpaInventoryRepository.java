package com.bbcc.kidly.boundedcontext.product.out.inventory;

import com.bbcc.kidly.boundedcontext.product.domain.inventory.Inventory;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaInventoryRepository extends JpaRepository<Inventory, Long>, InventoryRepository {
}
