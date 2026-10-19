package com.bbcc.kidly.boundedcontext.member.app;

import com.bbcc.kidly.boundedcontext.member.domain.MemberRepository;
import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.global.error.CommonErrorCode;
import com.bbcc.kidly.shared.seller.domain.SellerStatus;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원의 판매자 상태 복사본을 갱신한다. 역할은 다음 토큰 발급부터 바뀐다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SellerStateService {

    private final MemberRepository memberRepository;

    @Transactional
    public void apply(Long sellerId, SellerStatus status, LocalDateTime occurredAt) {
        memberRepository.findById(sellerId)
            .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND))
            .applySellerState(status, occurredAt);
        log.info("판매자 상태 반영: memberId={} status={}", sellerId, status);
    }
}
