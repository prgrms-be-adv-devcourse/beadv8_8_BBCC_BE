package com.bbcc.kidly.boundedcontext.notification.out;

import com.bbcc.kidly.boundedcontext.notification.domain.SellerStatusCopy;
import com.bbcc.kidly.boundedcontext.notification.domain.SellerStatusCopyRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaSellerStatusCopyRepository extends JpaRepository<SellerStatusCopy, Long>,
    SellerStatusCopyRepository {
}
