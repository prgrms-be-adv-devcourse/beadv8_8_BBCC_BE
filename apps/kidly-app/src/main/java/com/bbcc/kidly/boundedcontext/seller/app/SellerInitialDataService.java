package com.bbcc.kidly.boundedcontext.seller.app;

import com.bbcc.kidly.boundedcontext.seller.app.dto.SellerApplicationRejectRequest;
import com.bbcc.kidly.boundedcontext.seller.app.dto.SellerApplicationRequest;
import com.bbcc.kidly.boundedcontext.seller.app.dto.SellerApplicationResult;
import com.bbcc.kidly.boundedcontext.seller.domain.SellerApplicationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 기획서 7.1 공통 초기 데이터 중 판매자를 넣는다. 회원 초기 데이터의 관리자(회원 3)가 회원 2를 승인해 판매자 2(테스트상점)를 만들고,
 * 신청 목록 · 거절 화면 확인용으로 회원 5는 대기, 회원 6은 거절 상태 신청을 둔다.
 * 판매자 신청이 하나도 없을 때만 넣는다 (이미 있는 신청과 겹쳐 기동이 실패하지 않게).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SellerInitialDataService {

    private static final Long SELLER_ID = 2L;
    private static final Long ADMIN_ID = 3L;
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

    // 닉네임은 회원 초기 데이터의 닉네임과 같게 둔다 (신청 목록에 보이는 신청자 이름)
    private SellerApplicationResult submit(Long memberId, String nickname, String businessName,
        String businessRegistrationNumber) {
        var request = new SellerApplicationRequest(businessName, "홍길동", businessRegistrationNumber);
        return sellerApplicationService.submit(memberId, nickname, request);
    }
}
