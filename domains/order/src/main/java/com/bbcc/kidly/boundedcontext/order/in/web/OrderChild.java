package com.bbcc.kidly.boundedcontext.order.in.web;

/**
 * 주문 API를 부른 로그인한 자녀(#1000, {@link OrderChildCaller}).
 *
 * @param accountId 자녀 계정 ID
 * @param childProfileId 그 계정에 연결된 자녀 프로필 ID(주문상품의 입을 아이)
 * @param guardianId 보호자 회원 ID
 */
public record OrderChild(Long accountId, Long childProfileId, Long guardianId) {
}
