package com.bbcc.kidly.boundedcontext.recommend.domain.preference;

/**
 * 브랜드 하나에 대한 친밀도다. 점수는 0 이상 1 이하다.
 */
public record BrandAffinity(String brand, double score) {
}
