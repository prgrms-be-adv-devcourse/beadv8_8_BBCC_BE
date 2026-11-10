package com.bbcc.kidly.boundedcontext.recommend.domain.size;

/**
 * 사이즈를 고른 근거 하나다.
 *
 * @param detail 화면에 그대로 보여 주는 문구
 * @param sourceId 근거로 쓴 글 조각의 ID. 글 조각을 AI에 주지 않으면 null이다
 */
public record SizeReason(SizeReasonType type, String detail, String sourceId) {
}
