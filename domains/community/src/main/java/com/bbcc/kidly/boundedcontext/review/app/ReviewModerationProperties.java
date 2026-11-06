package com.bbcc.kidly.boundedcontext.review.app;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 리뷰 검사 실행 설정(kidly.community.moderation.*). 검사기 목록 · 금칙어 · AI 설정은 out/config의
 * CommunityModerationProperties가 같은 키 아래에서 따로 읽는다. 값이 틀리면 키 이름이 나오는 오류로 기동에 실패한다.
 *
 * @param enabled 주기 검사(스케줄러)를 켤지. 테스트는 끄고 처리 메서드를 직접 부른다
 * @param interval 검사 주기(이전 실행이 끝난 뒤부터)
 * @param batchSize 한 주기에 검사할 최대 건수
 * @param maxAttempts 판단 못 함(장애 · 시간 초과)을 몇 번까지 다시 시도할지. 닿으면 대기 목록에서 빠지고 공개 그대로.
 *     한도 초과(429)는 횟수에 넣지 않는다
 */
@Validated
@ConfigurationProperties("kidly.community.moderation")
public record ReviewModerationProperties(
    boolean enabled,
    @NotNull @DurationMin(seconds = 1) Duration interval,
    @Min(1) @Max(100) int batchSize,
    @Min(1) int maxAttempts) {
}
