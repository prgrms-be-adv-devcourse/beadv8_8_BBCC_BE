package com.bbcc.kidly.boundedcontext.recommend.out.purchase;

import com.bbcc.kidly.boundedcontext.recommend.app.port.purchase.PurchasedGarmentRow;
import com.bbcc.kidly.boundedcontext.recommend.app.port.purchase.PurchasedGarments;
import com.bbcc.kidly.boundedcontext.recommend.domain.purchase.PurchaseRecord;
import com.bbcc.kidly.boundedcontext.recommend.domain.purchase.PurchaseRecordRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaPurchaseRecordRepository
    extends JpaRepository<PurchaseRecord, Long>, PurchaseRecordRepository, PurchasedGarments {

    @Override
    @Query(value = """
        select new com.bbcc.kidly.boundedcontext.recommend.app.port.purchase.PurchasedGarmentRow(
            r.orderItemId, r.productId, r.size, r.confirmedAt)
        from PurchaseRecord r
        where r.childProfileId = :childProfileId
        order by r.confirmedAt desc, r.id desc
        """,
        countQuery = "select count(r) from PurchaseRecord r where r.childProfileId = :childProfileId")
    Page<PurchasedGarmentRow> findByChild(@Param("childProfileId") Long childProfileId, Pageable pageable);

    @Override
    @Query("""
        select distinct r.productId
        from PurchaseRecord r
        where r.childProfileId = :childProfileId and r.confirmedAt >= :since
        """)
    List<Long> findProductIdsConfirmedSince(@Param("childProfileId") Long childProfileId,
        @Param("since") LocalDateTime since);

    @Override
    @Query("""
        select r
        from PurchaseRecord r
        where r.childProfileId = :childProfileId and r.confirmedAt >= :since
        """)
    List<PurchaseRecord> findConfirmedSince(@Param("childProfileId") Long childProfileId,
        @Param("since") LocalDateTime since);
}
