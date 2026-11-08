package com.bbcc.kidly.boundedcontext.review.app;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * 쓰이지 않는 리뷰 사진 정리 설정(kidly.community.review.image.cleanup.*). 값이 틀리면 키 이름이 나오는 오류로 기동에 실패한다.
 *
 * @param enabled 정리 스케줄러를 켤지(기본 false). 로컬 · 테스트는 끈다(테스트는 처리 메서드를 직접 부른다). 켜도 저장소에 닿지 않는 설정이면 돌지 않는다
 * @param interval 정리 주기(이전 실행이 끝난 뒤부터)
 * @param batchSize 한 주기에 처리할 정리 대기 최대 건수
 * @param pendingDelay 정리 대기에 들어온 뒤 이만큼 지나야 지운다(빼었다가 다시 붙이는 경우를 지킨다, 최소 5분)
 * @param maxBackoff 실패한 대기를 다시 시도할 때 미루는 최대 시간(1분부터 두 배씩 늘려 이 값에서 멈춘다)
 * @param orphanGrace 저장소에 올라간 뒤 이만큼 지났고 어느 리뷰에도 붙지 않은 파일만 훑기로 지운다(업로드 주소 만료보다 길어야 한다.
 *     기동 때 out/config가 확인한다)
 * @param scanPageSize 훑기 한 번에 읽는 저장소 목록 수(1~1000)
 * @param scanDeleteLimit 훑기 한 주기에 지우는 최대 파일 수(성공한 삭제만 센다)
 */
@Validated
@ConfigurationProperties("kidly.community.review.image.cleanup")
public record ReviewImageCleanupProperties(
    @DefaultValue("false") boolean enabled,
    @NotNull @DurationMin(seconds = 1) Duration interval,
    @Min(1) @Max(1000) int batchSize,
    @NotNull @DurationMin(minutes = 5) Duration pendingDelay,
    @NotNull @DurationMin(minutes = 1) Duration maxBackoff,
    @NotNull @DurationMin(minutes = 10) Duration orphanGrace,
    @Min(1) @Max(1000) int scanPageSize,
    @Min(1) int scanDeleteLimit) {
}
