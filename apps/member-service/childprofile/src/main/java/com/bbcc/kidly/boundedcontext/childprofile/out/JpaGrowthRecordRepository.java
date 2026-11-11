package com.bbcc.kidly.boundedcontext.childprofile.out;

import com.bbcc.kidly.boundedcontext.childprofile.domain.GrowthRecord;
import com.bbcc.kidly.boundedcontext.childprofile.domain.GrowthRecordRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaGrowthRecordRepository extends JpaRepository<GrowthRecord, Long>, GrowthRecordRepository {
}
