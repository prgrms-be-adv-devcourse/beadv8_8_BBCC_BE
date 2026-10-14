package com.bbcc.kidly.boundedcontext.recommend.out.purchase;

import com.bbcc.kidly.boundedcontext.recommend.domain.purchase.PurchaseRecord;
import com.bbcc.kidly.boundedcontext.recommend.domain.purchase.PurchaseRecordRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaPurchaseRecordRepository extends JpaRepository<PurchaseRecord, Long>, PurchaseRecordRepository {
}
