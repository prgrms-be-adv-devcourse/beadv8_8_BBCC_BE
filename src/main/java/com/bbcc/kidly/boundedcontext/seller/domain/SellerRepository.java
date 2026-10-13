package com.bbcc.kidly.boundedcontext.seller.domain;

/**
 * 판매자 ID는 회원 ID와 같다.
 */
public interface SellerRepository {

    Seller save(Seller seller);

    boolean existsById(Long id);
}
