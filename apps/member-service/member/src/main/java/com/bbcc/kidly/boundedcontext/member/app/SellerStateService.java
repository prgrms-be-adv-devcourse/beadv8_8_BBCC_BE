package com.bbcc.kidly.boundedcontext.member.app;

import com.bbcc.kidly.boundedcontext.member.domain.MemberRepository;
import com.bbcc.kidly.member.support.error.BusinessException;
import com.bbcc.kidly.member.support.error.CommonErrorCode;
import com.bbcc.kidly.shared.auth.out.MemberTokenRevoker;
import com.bbcc.kidly.shared.seller.domain.SellerStatus;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SellerStateService {

    private final MemberRepository memberRepository;
    private final MemberTokenRevoker memberTokenRevoker;

    @Transactional
    public void apply(Long sellerId, SellerStatus status, LocalDateTime occurredAt) {
        var member = memberRepository.findByIdForUpdate(sellerId)
            .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (member.applySellerState(status, occurredAt)) {
            memberTokenRevoker.revoke(sellerId, LocalDateTime.now());
        }
        log.info("판매자 상태 반영: memberId={} status={}", sellerId, status);
    }
}
