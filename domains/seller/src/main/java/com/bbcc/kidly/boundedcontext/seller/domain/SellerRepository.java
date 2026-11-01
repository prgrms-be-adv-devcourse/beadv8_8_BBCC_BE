package com.bbcc.kidly.boundedcontext.seller.domain;

import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.global.error.CommonErrorCode;
import java.util.List;
import java.util.Optional;

/**
 * 판매자 ID는 회원 ID와 같다.
 */
public interface SellerRepository {

    Seller save(Seller seller);

    Optional<Seller> findById(Long id);

    boolean existsById(Long id);

    List<Seller> findAll();

    default Seller getById(Long id) {
        return findById(id).orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }
}
