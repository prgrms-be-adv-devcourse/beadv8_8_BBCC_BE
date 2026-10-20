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
 * 회원의 판매자 상태 복사본을 갱신한다. SELLER 역할을 얻는 것은 다음 토큰 발급부터, 잃는 것은 Access Token을 무효화해 바로 반영된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SellerStateService {

    private final MemberRepository memberRepository;
    private final AccessTokenRevoker accessTokenRevoker;

    @Transactional
    public void apply(Long sellerId, SellerStatus status, LocalDateTime occurredAt) {
        var member = memberRepository.findById(sellerId)
            .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        var wasActiveSeller = member.isActiveSeller();
        member.applySellerState(status, occurredAt);
        // SELLER 역할을 잃었으면 지금 토큰의 SELLER도 바로 막는다
        if (wasActiveSeller && !member.isActiveSeller()) {
            accessTokenRevoker.revoke(sellerId, LocalDateTime.now());
        }
        log.info("판매자 상태 반영: memberId={} status={}", sellerId, status);
    }
}
