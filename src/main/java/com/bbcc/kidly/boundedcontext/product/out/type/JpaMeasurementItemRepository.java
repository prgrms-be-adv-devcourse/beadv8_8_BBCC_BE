package com.bbcc.kidly.boundedcontext.product.out.type;

import com.bbcc.kidly.boundedcontext.product.domain.type.MeasurementItem;
import com.bbcc.kidly.boundedcontext.product.domain.type.MeasurementItemRepository;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface JpaMeasurementItemRepository extends JpaRepository<MeasurementItem, Long>, MeasurementItemRepository {

    @Override
    @Query("select m from MeasurementItem m order by m.id")
    List<MeasurementItem> findAll();
}
