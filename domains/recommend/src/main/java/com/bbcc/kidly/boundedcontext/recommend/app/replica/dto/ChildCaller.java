package com.bbcc.kidly.boundedcontext.recommend.app.replica.dto;

/**
 * 자녀 토큰으로 부른 추천의 계산 대상이다. 보호자 회원이 그 자녀를 고른 것과 같은 값이라 회원 추천 유스케이스에 그대로 넘긴다.
 *
 * @param guardianId 보호자 회원 ID
 * @param childProfileId 자녀 계정에 연결된 자녀 프로필 ID
 */
public record ChildCaller(Long guardianId, Long childProfileId) {
}
