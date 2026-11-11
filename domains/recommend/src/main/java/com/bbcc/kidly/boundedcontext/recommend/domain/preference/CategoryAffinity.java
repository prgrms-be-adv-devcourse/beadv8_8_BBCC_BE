package com.bbcc.kidly.boundedcontext.recommend.domain.preference;

/**
 * 카테고리 하나에 대한 친밀도다. 점수는 0 이상 1 이하다.
 */
public record CategoryAffinity(Long categoryId, double score) {
}
