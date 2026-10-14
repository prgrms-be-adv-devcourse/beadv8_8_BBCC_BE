package com.bbcc.kidly.boundedcontext.seller.app;

import com.bbcc.kidly.boundedcontext.seller.app.dto.SellerResult;
import com.bbcc.kidly.boundedcontext.seller.domain.SellerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 판매자 정보 조회.
 */
@Service
@RequiredArgsConstructor
public class SellerService {

    private final SellerRepository sellerRepository;

    @Transactional(readOnly = true)
    public SellerResult getMine(Long sellerId) {
        return SellerResult.from(sellerRepository.getById(sellerId));
    }
}
