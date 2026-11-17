package com.bbcc.kidly.boundedcontext.seller.app;

import com.bbcc.kidly.boundedcontext.seller.app.dto.SellerApplicationRejectRequest;
import com.bbcc.kidly.boundedcontext.seller.app.dto.SellerApplicationRequest;
import com.bbcc.kidly.boundedcontext.seller.app.dto.SellerApplicationResult;
import com.bbcc.kidly.boundedcontext.seller.domain.SellerApplicationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SellerInitialDataService {

    private static final Long SELLER_ID = 2L;
    private static final Long ADMIN_ID = 1L;
    private static final Long PENDING_APPLICANT_ID = 5L;
    private static final Long REJECTED_APPLICANT_ID = 6L;

    private final SellerApplicationService sellerApplicationService;
    private final SellerApplicationRepository sellerApplicationRepository;

    @Transactional
    public void load() {
        if (sellerApplicationRepository.count() > 0) {
            log.info("판매자 신청이 이미 있어 판매자 초기 데이터를 넣지 않는다");
            return;
        }
        var approved = submit(SELLER_ID, "테스트판매자", "테스트상점", "1000000002");
        sellerApplicationService.approve(approved.id(), ADMIN_ID);
        submit(PENDING_APPLICANT_ID, "신청대기회원", "대기상점", "1000000005");
        var rejected = submit(REJECTED_APPLICANT_ID, "신청거절회원", "거절상점", "1000000006");
        sellerApplicationService.reject(
            rejected.id(), ADMIN_ID, new SellerApplicationRejectRequest("거절 화면 확인용 초기 데이터"));
        log.info("판매자 초기 데이터를 넣었다");
    }

    private SellerApplicationResult submit(Long memberId, String nickname, String businessName,
        String businessRegistrationNumber) {
        var request = new SellerApplicationRequest(businessName, "홍길동", businessRegistrationNumber);
        return sellerApplicationService.submit(memberId, nickname, request);
    }
}
