package com.bbcc.kidly.boundedcontext.product.out.type;

import com.bbcc.kidly.boundedcontext.product.domain.type.MeasurementItem;
import com.bbcc.kidly.boundedcontext.product.domain.type.MeasurementItemRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaMeasurementItemRepository extends JpaRepository<MeasurementItem, Long>, MeasurementItemRepository {
}
