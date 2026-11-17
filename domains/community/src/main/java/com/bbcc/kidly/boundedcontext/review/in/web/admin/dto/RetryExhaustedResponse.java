package com.bbcc.kidly.boundedcontext.review.in.web.admin.dto;

/**
 * 상한 리뷰 재검사 결과.
 *
 * @param resetCount 다시 검사 대기로 되돌린 리뷰 수
 */
public record RetryExhaustedResponse(int resetCount) {
}
