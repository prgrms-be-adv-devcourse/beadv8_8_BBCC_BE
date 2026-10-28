package com.bbcc.kidly.boundedcontext.seller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.seller.domain.Seller;
import com.bbcc.kidly.boundedcontext.seller.domain.SellerApplication;
import com.bbcc.kidly.boundedcontext.seller.domain.SellerApplicationSubmitInfo;
import com.bbcc.kidly.boundedcontext.seller.domain.SellerRepository;
import com.bbcc.kidly.boundedcontext.seller.out.JpaSellerRepository;
import com.bbcc.kidly.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

@IntegrationTest
class SellerRepositoryIntegrationTest {

    private static final Long MEMBER_ID = 1L;

    @Autowired
    private SellerRepository sellerRepository;

    @Autowired
    private JpaSellerRepository jpaSellerRepository;

    @Test
    @DisplayName("판매자를 저장하면 회원 ID로 존재를 확인할 수 있다")
    void save() {
        sellerRepository.save(Seller.from(SellerFixture.approvedApplication(MEMBER_ID)));

        assertThat(sellerRepository.existsById(MEMBER_ID)).isTrue();
        assertThat(sellerRepository.existsById(2L)).isFalse();
    }

    @Test
    @DisplayName("같은 회원의 판매자를 다시 저장하면 덮어쓰지 않고 실패한다")
    void saveDuplicate() {
        sellerRepository.save(Seller.from(SellerFixture.approvedApplication(MEMBER_ID)));
        var other = SellerApplication.submit(new SellerApplicationSubmitInfo(
            MEMBER_ID, SellerFixture.APPLICANT_NICKNAME, "다른상점", SellerFixture.REPRESENTATIVE_NAME,
            SellerFixture.BUSINESS_REGISTRATION_NUMBER));
        other.approve(SellerFixture.ADMIN_ID, SellerFixture.NOW);

        assertThatThrownBy(() -> sellerRepository.save(Seller.from(other)))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jpaSellerRepository.findAll()).singleElement()
            .satisfies(seller -> assertThat(seller.getBusinessName()).isEqualTo(SellerFixture.BUSINESS_NAME));
    }
}
