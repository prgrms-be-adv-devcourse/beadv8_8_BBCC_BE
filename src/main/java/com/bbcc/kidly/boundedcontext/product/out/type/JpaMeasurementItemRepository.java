package com.bbcc.kidly.boundedcontext.product.out.type;

import com.bbcc.kidly.boundedcontext.product.domain.type.MeasurementItem;
import com.bbcc.kidly.boundedcontext.product.domain.type.MeasurementItemRepository;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface JpaMeasurementItemRepository extends JpaRepository<MeasurementItem, Long>, MeasurementItemRepository {

    // JpaRepository.getById(프록시 반환)와 충돌하므로 도메인 쪽 getById(404)를 명시적으로 고른다
    @Override
    default MeasurementItem getById(Long id) {
        return MeasurementItemRepository.super.getById(id);
    }

    @Override
    @Query("select m from MeasurementItem m order by m.id")
    List<MeasurementItem> findAll();
}
