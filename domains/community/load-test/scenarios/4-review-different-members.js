// 시나리오 4: 서로 다른 회원 N명이 같은 상품에 리뷰를 동시에 쓴다 → 모두 201, reviewCount = N, 별점 합 정확
// 회원 i(9400001~9400000+N)는 자기 주문상품(같은 번호)에 별점 (i % 5) + 1을 쓴다. 상품 9000004
// 별점 합은 목록을 끝까지 읽어 더한 값으로, 평균은 요약의 averageRating(합 ÷ 수, 소수 첫째 자리 HALF_UP)으로 본다
import http from 'k6/http';
import { check } from 'k6';
import { BASE_URL, allReviews, withHeaders, concurrency, memberAuth, oneShotOptions, requireZero, reviewSummary,
  startAt, startTogether } from '../lib/common.js';

const N = concurrency(50);
const PRODUCT_ID = 9000004;
const FIRST_MEMBER_ID = 9400001;

const ratingOf = (memberId) => (memberId % 5) + 1;
const memberIds = Array.from({ length: N }, (_, i) => FIRST_MEMBER_ID + i);
const EXPECTED_SUM = memberIds.reduce((sum, id) => sum + ratingOf(id), 0);
// 합 ÷ N을 소수 첫째 자리에서 HALF_UP(양수라 정수 계산으로 같다)
const EXPECTED_AVERAGE = Math.floor((20 * EXPECTED_SUM + N) / (2 * N)) / 10;

export const options = oneShotOptions(N, { 'http_req_failed{op:write}': ['rate==0'] });

export function setup() {
  requireZero('리뷰 수', reviewSummary(PRODUCT_ID).reviewCount);
  return { members: memberIds.map((id) => memberAuth(id)), at: startAt() };
}

export default function (data) {
  const memberId = memberIds[__VU - 1];
  startTogether(data.at);
  const res = http.post(`${BASE_URL}/api/v1/reviews`,
    JSON.stringify({ orderItemId: String(memberId), rating: ratingOf(memberId), content: `동시 작성 ${memberId}` }),
    { headers: withHeaders(data.members[__VU - 1], { 'Content-Type': 'application/json' }), tags: { op: 'write' } });
  check(res, { '리뷰 201': (r) => r.status === 201 });
}

export function teardown() {
  const summary = reviewSummary(PRODUCT_ID);
  const items = allReviews(PRODUCT_ID);
  const sum = items.reduce((acc, item) => acc + item.rating, 0);
  const distinct = new Set(items.map((item) => item.orderItemId)).size;
  console.log(`[결과] reviewCount 기대=${N} 실제=${summary.reviewCount}, 별점 합 기대=${EXPECTED_SUM} 실제=${sum}, `
    + `평균 기대=${EXPECTED_AVERAGE} 실제=${summary.averageRating}`);
  check(summary, {
    [`reviewCount = N(${N})`]: (s) => s.reviewCount === N,
    [`averageRating = ${EXPECTED_AVERAGE}`]: (s) => Math.abs(Number(s.averageRating) - EXPECTED_AVERAGE) < 1e-9,
  });
  check(items, {
    [`목록 ${N}건 · 주문상품 겹침 없음`]: (v) => v.length === N && distinct === N,
    [`별점 합 = ${EXPECTED_SUM}`]: () => sum === EXPECTED_SUM,
  });
}
