package com.bbcc.kidly.boundedcontext.payment.app;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 충전 정리 설정.
 *
 * @param confirmingStaleAfter 승인 중으로 이만큼 남은 충전을 토스 조회로 확정한다. 토스 읽기 시간 제한(30초)보다 길어야
 *                             진행 중인 사용자 승인과 겹치지 않는다
 */
@ConfigurationProperties("kidly.payment.charge")
public record ChargeProperties(@DefaultValue("PT5M") Duration confirmingStaleAfter) {
}
