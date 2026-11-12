package com.bbcc.kidly.boundedcontext.payment.out;

import com.bbcc.kidly.boundedcontext.payment.domain.DailyPaymentTotal;
import com.bbcc.kidly.boundedcontext.payment.domain.Payment;
import com.bbcc.kidly.boundedcontext.payment.domain.PaymentRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaPaymentRepository extends JpaRepository<Payment, Long>, PaymentRepository {

    @Override
    @Query("""
        select new com.bbcc.kidly.boundedcontext.payment.domain.DailyPaymentTotal(
            cast(p.paidAt as LocalDate), sum(p.amount), count(p))
        from Payment p
        where p.paidAt >= :from and p.paidAt < :to
        group by cast(p.paidAt as LocalDate)
        order by cast(p.paidAt as LocalDate)
        """)
    List<DailyPaymentTotal> sumDailyPaidBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
