package com.bbcc.kidly.boundedcontext.payment.out;

import com.bbcc.kidly.boundedcontext.payment.domain.BalanceReturn;
import com.bbcc.kidly.boundedcontext.payment.domain.BalanceReturnRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaBalanceReturnRepository extends JpaRepository<BalanceReturn, Long>, BalanceReturnRepository {
}
