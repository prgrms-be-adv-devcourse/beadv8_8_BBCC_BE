package com.bbcc.kidly.boundedcontext.notification.domain;

import java.util.Optional;

public interface SellerStatusCopyRepository {

    SellerStatusCopy save(SellerStatusCopy sellerStatusCopy);

    Optional<SellerStatusCopy> findById(Long sellerId);
}
