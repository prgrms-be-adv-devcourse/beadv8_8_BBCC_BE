package com.bbcc.kidly.boundedcontext.activity.app;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * 커뮤니티 이벤트 릴레이 설정(kidly.community.events.relay.*). 값이 틀리면 키 이름이 나오는 오류로 기동에 실패한다.
 *
 * @param interval 릴레이 주기(이전 실행이 끝난 뒤부터)
 * @param batchSize 한 주기에 볼 최대 행 수
 * @param maxAttempts 이만큼 실패하면 격리하고 다음 행으로 넘어간다
 * @param initialBackoff 첫 실패 뒤 다시 보내기까지(실패마다 두 배)
 * @param maxBackoff 다시 보내기까지의 상한
 * @param sendTimeout 한 건을 보내고 브로커 응답을 기다리는 최대 시간
 */
@Validated
@ConfigurationProperties("kidly.community.events.relay")
public record CommunityEventRelayProperties(
    @DefaultValue("PT1S") @NotNull @DurationMin(millis = 100) Duration interval,
    @DefaultValue("100") @Min(1) @Max(1000) int batchSize,
    @DefaultValue("10") @Min(1) int maxAttempts,
    @DefaultValue("PT1S") @NotNull @DurationMin(millis = 1) Duration initialBackoff,
    @DefaultValue("PT60S") @NotNull @DurationMin(millis = 1) Duration maxBackoff,
    @DefaultValue("PT10S") @NotNull @DurationMin(millis = 1) Duration sendTimeout) {
}
