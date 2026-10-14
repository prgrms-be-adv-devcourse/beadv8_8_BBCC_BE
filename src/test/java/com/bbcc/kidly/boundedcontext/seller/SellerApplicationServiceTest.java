package com.bbcc.kidly.boundedcontext.seller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.bbcc.kidly.boundedcontext.seller.app.SellerApplicationService;
import com.bbcc.kidly.boundedcontext.seller.app.dto.SellerApplicationRequest;
import com.bbcc.kidly.boundedcontext.seller.app.port.SellerApplicationSummary;
import com.bbcc.kidly.boundedcontext.seller.app.port.SellerApplicationSummaryQuery;
import com.bbcc.kidly.boundedcontext.seller.domain.Seller;
import com.bbcc.kidly.boundedcontext.seller.domain.SellerApplication;
import com.bbcc.kidly.boundedcontext.seller.domain.SellerApplicationRepository;
import com.bbcc.kidly.boundedcontext.seller.domain.SellerErrorCode;
import com.bbcc.kidly.boundedcontext.seller.domain.SellerRepository;
import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.global.error.CommonErrorCode;
import com.bbcc.kidly.global.eventpublisher.EventPublisher;
import com.bbcc.kidly.shared.seller.domain.SellerApplicationStatus;
import com.bbcc.kidly.shared.seller.domain.SellerStatus;
import com.bbcc.kidly.shared.seller.event.SellerChangedEvent;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class SellerApplicationServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final String NICKNAME = SellerFixture.APPLICANT_NICKNAME;
    private static final SellerApplicationRequest REQUEST = new SellerApplicationRequest(
        SellerFixture.BUSINESS_NAME, SellerFixture.REPRESENTATIVE_NAME, SellerFixture.BUSINESS_REGISTRATION_NUMBER);

    @Mock
    private SellerApplicationRepository sellerApplicationRepository;

    @Mock
    private SellerRepository sellerRepository;

    @Mock
    private SellerApplicationSummaryQuery sellerApplicationSummaryQuery;

    @Mock
    private EventPublisher eventPublisher;

    @InjectMocks
    private SellerApplicationService sellerApplicationService;

    @Test
    @DisplayName("판매자도 아니고 심사 중인 신청도 없으면 신청을 저장하고 PENDING으로 준다")
    void submit() {
        given(sellerRepository.existsById(MEMBER_ID)).willReturn(false);
        given(sellerApplicationRepository.existsByApplicantIdAndStatus(MEMBER_ID, SellerApplicationStatus.PENDING))
            .willReturn(false);
        given(sellerApplicationRepository.save(any(SellerApplication.class)))
            .willAnswer(invocation -> invocation.getArgument(0));

        var result = sellerApplicationService.submit(MEMBER_ID, NICKNAME, REQUEST);

        var captor = ArgumentCaptor.forClass(SellerApplication.class);
        then(sellerApplicationRepository).should().save(captor.capture());
        assertThat(captor.getValue().getApplicantId()).isEqualTo(MEMBER_ID);
        assertThat(captor.getValue().getApplicantNickname()).isEqualTo(NICKNAME);
        assertThat(result.status()).isEqualTo(SellerApplicationStatus.PENDING);
        assertThat(result.businessName()).isEqualTo(SellerFixture.BUSINESS_NAME);
        assertThat(result.representativeName()).isEqualTo(SellerFixture.REPRESENTATIVE_NAME);
        assertThat(result.maskedBusinessRegistrationNumber())
            .isEqualTo(SellerFixture.MASKED_BUSINESS_REGISTRATION_NUMBER);
        assertThat(result.decidedAt()).isNull();
    }

    @Test
    @DisplayName("이미 판매자면 ALREADY_SELLER이고 저장하지 않는다")
    void submitWhenSeller() {
        given(sellerRepository.existsById(MEMBER_ID)).willReturn(true);

        assertThatThrownBy(() -> sellerApplicationService.submit(MEMBER_ID, NICKNAME, REQUEST))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(SellerErrorCode.ALREADY_SELLER);
        then(sellerApplicationRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("심사 중인 신청이 있으면 APPLICATION_ALREADY_PENDING이고 저장하지 않는다")
    void submitWhenPending() {
        given(sellerRepository.existsById(MEMBER_ID)).willReturn(false);
        given(sellerApplicationRepository.existsByApplicantIdAndStatus(MEMBER_ID, SellerApplicationStatus.PENDING))
            .willReturn(true);

        assertThatThrownBy(() -> sellerApplicationService.submit(MEMBER_ID, NICKNAME, REQUEST))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(SellerErrorCode.APPLICATION_ALREADY_PENDING);
        then(sellerApplicationRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("가장 최근 신청을 준다")
    void getLatest() {
        given(sellerApplicationRepository.findFirstByApplicantIdOrderByIdDesc(MEMBER_ID))
            .willReturn(Optional.of(SellerFixture.application(MEMBER_ID)));

        var result = sellerApplicationService.getLatest(MEMBER_ID);

        assertThat(result.status()).isEqualTo(SellerApplicationStatus.PENDING);
        assertThat(result.businessName()).isEqualTo(SellerFixture.BUSINESS_NAME);
    }

    @Test
    @DisplayName("신청한 적이 없으면 NOT_FOUND다")
    void getLatestWhenNone() {
        given(sellerApplicationRepository.findFirstByApplicantIdOrderByIdDesc(MEMBER_ID))
            .willReturn(Optional.empty());

        assertThatThrownBy(() -> sellerApplicationService.getLatest(MEMBER_ID))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(CommonErrorCode.NOT_FOUND);
    }

    @Test
    @DisplayName("신청 목록 조회는 조회 포트에 넘긴다")
    void getApplications() {
        var pageable = PageRequest.of(0, 20);
        var summary = SellerApplicationSummary.from(SellerFixture.application(MEMBER_ID));
        given(sellerApplicationSummaryQuery.findAll(SellerApplicationStatus.PENDING, pageable))
            .willReturn(new PageImpl<>(List.of(summary), pageable, 1));

        var page = sellerApplicationService.getApplications(SellerApplicationStatus.PENDING, pageable);

        assertThat(page.getContent()).containsExactly(summary);
    }

    @Test
    @DisplayName("승인하면 신청을 APPROVED로 바꾸고 판매자를 저장하고 SellerChangedEvent를 발행한다")
    void approve() {
        var application = SellerFixture.application(MEMBER_ID);
        given(sellerApplicationRepository.getById(1L)).willReturn(application);
        given(sellerRepository.existsById(MEMBER_ID)).willReturn(false);
        given(sellerRepository.save(any(Seller.class))).willAnswer(invocation -> invocation.getArgument(0));

        var result = sellerApplicationService.approve(1L, SellerFixture.ADMIN_ID);

        assertThat(result.status()).isEqualTo(SellerApplicationStatus.APPROVED);
        assertThat(application.getStatus()).isEqualTo(SellerApplicationStatus.APPROVED);
        then(sellerRepository).should().save(any(Seller.class));
        var captor = ArgumentCaptor.forClass(SellerChangedEvent.class);
        then(eventPublisher).should().publish(captor.capture());
        var data = captor.getValue().data();
        assertThat(data.sellerId()).isEqualTo(MEMBER_ID);
        assertThat(data.businessName()).isEqualTo(SellerFixture.BUSINESS_NAME);
        assertThat(data.status()).isEqualTo(SellerStatus.ACTIVE);
        assertThat(application.getDecidedBy()).isEqualTo(SellerFixture.ADMIN_ID);
        assertThat(captor.getValue().occurredAt()).isEqualTo(application.getDecidedAt());
    }

    @Test
    @DisplayName("이미 판매자인 회원의 신청을 승인하면 ALREADY_SELLER이고 저장도 발행도 하지 않는다")
    void approveWhenSeller() {
        given(sellerApplicationRepository.getById(1L)).willReturn(SellerFixture.application(MEMBER_ID));
        given(sellerRepository.existsById(MEMBER_ID)).willReturn(true);

        assertThatThrownBy(() -> sellerApplicationService.approve(1L, SellerFixture.ADMIN_ID))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(SellerErrorCode.ALREADY_SELLER);
        then(sellerRepository).should(never()).save(any());
        then(eventPublisher).should(never()).publish(any());
    }

    @Test
    @DisplayName("심사 중이 아닌 신청을 승인하면 APPLICATION_NOT_PENDING이고 발행하지 않는다")
    void approveNotPending() {
        given(sellerApplicationRepository.getById(1L)).willReturn(SellerFixture.approvedApplication(MEMBER_ID));

        assertThatThrownBy(() -> sellerApplicationService.approve(1L, SellerFixture.ADMIN_ID))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(SellerErrorCode.APPLICATION_NOT_PENDING);
        then(eventPublisher).should(never()).publish(any());
    }
}
