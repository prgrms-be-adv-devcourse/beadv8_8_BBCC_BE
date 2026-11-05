package com.bbcc.kidly.boundedcontext.notification.out;

import com.bbcc.kidly.boundedcontext.notification.domain.ProductName;
import com.bbcc.kidly.boundedcontext.notification.domain.ProductNameRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaProductNameRepository extends JpaRepository<ProductName, Long>, ProductNameRepository {
}
