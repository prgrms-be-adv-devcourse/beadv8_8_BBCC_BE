package com.bbcc.kidly.boundedcontext.seller.out;

import com.bbcc.kidly.boundedcontext.seller.domain.Seller;
import com.bbcc.kidly.boundedcontext.seller.domain.SellerRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaSellerRepository extends JpaRepository<Seller, Long>, SellerRepository {

    // JpaRepository.getById(프록시 반환)와 충돌하므로 도메인 쪽 getById(404)를 명시적으로 고른다
    @Override
    default Seller getById(Long id) {
        return SellerRepository.super.getById(id);
    }
}
