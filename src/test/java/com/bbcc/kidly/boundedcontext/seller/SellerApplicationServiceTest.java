package com.bbcc.kidly.boundedcontext.seller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.bbcc.kidly.boundedcontext.seller.app.SellerApplicationService;
import com.bbcc.kidly.boundedcontext.seller.app.dto.SellerApplicationRequest;
import com.bbcc.kidly.boundedcontext.seller.domain.SellerApplication;
import com.bbcc.kidly.boundedcontext.seller.domain.SellerApplicationRepository;
import com.bbcc.kidly.boundedcontext.seller.domain.SellerErrorCode;
import com.bbcc.kidly.boundedcontext.seller.domain.SellerRepository;
import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.global.error.CommonErrorCode;
import com.bbcc.kidly.shared.seller.domain.SellerApplicationStatus;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
        assertThat(result.businessRegistrationNumber()).isEqualTo(SellerFixture.BUSINESS_REGISTRATION_NUMBER);
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
}
