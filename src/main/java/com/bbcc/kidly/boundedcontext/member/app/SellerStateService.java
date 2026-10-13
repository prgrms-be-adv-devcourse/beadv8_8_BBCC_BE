package com.bbcc.kidly.boundedcontext.member.app;

import com.bbcc.kidly.boundedcontext.member.domain.MemberRepository;
import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.global.error.CommonErrorCode;
import com.bbcc.kidly.shared.seller.domain.SellerStatus;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원의 판매자 상태 복사본을 갱신한다. 역할은 다음 토큰 발급부터 바뀐다.
 */
@Service
@RequiredArgsConstructor
public class SellerStateService {

    private final MemberRepository memberRepository;

    @Transactional
    public void apply(Long sellerId, SellerStatus status, LocalDateTime occurredAt) {
        memberRepository.findById(sellerId)
            .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND))
            .applySellerState(status, occurredAt);
    }
}
