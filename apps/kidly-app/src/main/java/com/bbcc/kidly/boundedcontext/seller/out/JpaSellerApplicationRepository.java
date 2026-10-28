package com.bbcc.kidly.boundedcontext.seller.out;

import com.bbcc.kidly.boundedcontext.seller.domain.SellerApplication;
import com.bbcc.kidly.boundedcontext.seller.domain.SellerApplicationRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaSellerApplicationRepository
    extends JpaRepository<SellerApplication, Long>, SellerApplicationRepository {

    // JpaRepository.getById(프록시 반환)와 충돌하므로 도메인 쪽 getById(404)를 명시적으로 고른다
    @Override
    default SellerApplication getById(Long id) {
        return SellerApplicationRepository.super.getById(id);
    }
}
