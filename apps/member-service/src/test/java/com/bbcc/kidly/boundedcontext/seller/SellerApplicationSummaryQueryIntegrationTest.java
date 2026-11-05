package com.bbcc.kidly.boundedcontext.seller;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.seller.app.port.SellerApplicationSummary;
import com.bbcc.kidly.boundedcontext.seller.app.port.SellerApplicationSummaryQuery;
import com.bbcc.kidly.boundedcontext.seller.domain.SellerApplicationRepository;
import com.bbcc.kidly.shared.seller.domain.SellerApplicationStatus;
import com.bbcc.kidly.support.IntegrationTest;
import java.util.Comparator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

@IntegrationTest
class SellerApplicationSummaryQueryIntegrationTest {

    @Autowired
    private SellerApplicationRepository sellerApplicationRepository;

    @Autowired
    private SellerApplicationSummaryQuery query;

    @BeforeEach
    void setUp() {
        sellerApplicationRepository.save(SellerFixture.application(1L));
        sellerApplicationRepository.save(SellerFixture.application(2L));
        sellerApplicationRepository.save(SellerFixture.approvedApplication(3L));
    }

    @Test
    @DisplayName("상태를 주면 그 상태의 신청만 페이지로 읽고 사업자등록번호는 가려진다")
    void findAllByStatus() {
        var page = query.findAll(SellerApplicationStatus.PENDING, PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).extracting(SellerApplicationSummary::status)
            .containsOnly(SellerApplicationStatus.PENDING);
        assertThat(page.getContent()).extracting(SellerApplicationSummary::maskedBusinessRegistrationNumber)
            .containsOnly(SellerFixture.MASKED_BUSINESS_REGISTRATION_NUMBER);
    }

    @Test
    @DisplayName("상태가 없으면 모든 신청을 페이지로 읽는다")
    void findAllWithoutStatus() {
        var page = query.findAll(null, PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(3);
    }

    @Test
    @DisplayName("최신 신청(id 내림차순)부터 읽는다")
    void findAllNewestFirst() {
        var page = query.findAll(null, PageRequest.of(0, 10));

        var ids = page.getContent().stream().map(SellerApplicationSummary::id).toList();
        assertThat(ids).isSortedAccordingTo(Comparator.reverseOrder());
    }

    @Test
    @DisplayName("페이지 크기만큼 읽고 전체 개수와 다음 페이지 여부를 알려준다")
    void findAllPaged() {
        var page = query.findAll(null, PageRequest.of(0, 2));

        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.hasNext()).isTrue();
    }
}
